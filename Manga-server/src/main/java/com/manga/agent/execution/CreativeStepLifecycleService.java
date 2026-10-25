package com.manga.agent.execution;

import com.manga.agent.application.CreativeEventService;
import com.manga.agent.application.CreativeRunAggregationService;
import com.manga.agent.config.CreativeExecutionProperties;
import com.manga.agent.domain.CreativeErrorCode;
import com.manga.agent.domain.CreativeEventType;
import com.manga.agent.domain.CreativeRuntimeMessages;
import com.manga.agent.domain.CreativeStepStatus;
import com.manga.agent.domain.CreativeToolStatus;
import com.manga.agent.domain.SystemActor;
import com.manga.agent.event.ContentDeltaEventPayload;
import com.manga.agent.event.CreativeEventSources;
import com.manga.agent.event.StepCompletedEventPayload;
import com.manga.agent.event.ToolExecutionEventPayload;
import com.manga.agent.harness.AgentRuntimeException;
import com.manga.entity.CreativeStep;
import com.manga.repository.CreativeRunRepository;
import com.manga.repository.CreativeStepRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * 使用 owner epoch 围栏提交执行中事件和 Step 终态。
 */
@Service
@RequiredArgsConstructor
public class CreativeStepLifecycleService {

    private final CreativeStepRepository stepRepository;
    private final CreativeRunRepository runRepository;
    private final CreativeEventService eventService;
    private final CreativeRunAggregationService aggregationService;
    private final CreativeExecutionProperties properties;

    /** 判断当前执行器是否仍持有有效运行权；取消或租约过期时返回 false。 */
    @Transactional(readOnly = true)
    public boolean canContinue(ClaimedCreativeStep claimed) {
        CreativeStep current = stepRepository.findByStepId(claimed.stepId()).orElse(null);
        return Objects.nonNull(current)
                && current.getStatus() == CreativeStepStatus.RUNNING
                && claimed.ownerInstanceId().equals(current.getOwnerInstanceId())
                && claimed.ownerEpoch() == current.getOwnerEpoch()
                && Objects.nonNull(current.getLeaseUntil())
                && current.getLeaseUntil().isAfter(LocalDateTime.now());
    }

    /** 在校验 owner epoch 后追加一段可恢复的流式文本事件。 */
    @Transactional
    public boolean appendDelta(ClaimedCreativeStep claimed, String delta) {
        return appendOwnedEvent(claimed, CreativeEventType.CONTENT_DELTA,
                new ContentDeltaEventPayload(delta));
    }

    /** 追加不暴露参数和结果正文的工具开始事件。 */
    @Transactional
    public boolean appendToolStarted(ClaimedCreativeStep claimed, String toolCallId, String toolName) {
        return appendOwnedEvent(claimed, CreativeEventType.TOOL_STARTED,
                new ToolExecutionEventPayload(toolCallId, toolName, CreativeToolStatus.RUNNING));
    }

    /** 追加不暴露参数和结果正文的工具完成事件。 */
    @Transactional
    public boolean appendToolCompleted(
            ClaimedCreativeStep claimed,
            String toolCallId,
            String toolName,
            CreativeToolStatus status) {
        return appendOwnedEvent(claimed, CreativeEventType.TOOL_COMPLETED,
                new ToolExecutionEventPayload(toolCallId, toolName, status));
    }

    /** 周期续签 Step 执行租约；取消、过期或 owner 变化时返回 false。 */
    @Transactional
    public boolean renewLease(ClaimedCreativeStep claimed) {
        LocalDateTime now = LocalDateTime.now();
        if (Objects.isNull(claimed.deadlineAt()) || !claimed.deadlineAt().isAfter(now)) {
            return false;
        }
        return stepRepository.renewLease(claimed.stepId(), claimed.ownerInstanceId(),
                claimed.ownerEpoch(), now, now.plus(properties.stepLease()),
                SystemActor.STEP_EXECUTION.code());
    }

    /** 在 owner epoch 和有效租约校验后追加一条 Agent 可见事件。 */
    private boolean appendOwnedEvent(
            ClaimedCreativeStep claimed,
            CreativeEventType eventType,
            Object payload) {
        runRepository.lockByRunId(claimed.runId());
        if (!renewLease(claimed)) {
            return false;
        }
        eventService.append(claimed.runId(), claimed.stepId(), eventType,
                CreativeEventSources.AGENT_SCOPE, null, payload);
        return true;
    }

    /** 使用围栏原子完成 Step，然后触发 Run 聚合。 */
    @Transactional
    public boolean complete(ClaimedCreativeStep claimed, String outputJson) {
        runRepository.lockByRunId(claimed.runId());
        boolean completed = stepRepository.completeOwned(claimed.stepId(), claimed.ownerInstanceId(),
                claimed.ownerEpoch(), outputJson, LocalDateTime.now(), SystemActor.STEP_EXECUTION.code());
        if (!completed) {
            acknowledgeCancellation(claimed);
            return false;
        }
        eventService.append(claimed.runId(), claimed.stepId(), CreativeEventType.STEP_COMPLETED,
                CreativeEventSources.RUNTIME, null,
                new StepCompletedEventPayload(claimed.stepId(), claimed.ownerEpoch()));
        aggregationService.aggregate(claimed.runId());
        return true;
    }

    /** 使用围栏原子失败 Step，并触发 Run 聚合。 */
    @Transactional
    public void fail(ClaimedCreativeStep claimed, Throwable failure) {
        runRepository.lockByRunId(claimed.runId());
        AgentRuntimeException runtimeException = failure instanceof AgentRuntimeException agentFailure
                ? agentFailure
                : null;
        String errorMessage = Objects.nonNull(runtimeException)
                ? runtimeException.getMessage()
                : CreativeRuntimeMessages.AGENT_STEP_EXECUTION_FAILED;
        CreativeErrorCode errorCode = Objects.nonNull(runtimeException)
                ? runtimeException.errorCode()
                : CreativeErrorCode.CAPABILITY_EXECUTION_FAILED;
        boolean failed = stepRepository.failOwned(claimed.stepId(), claimed.ownerInstanceId(),
                claimed.ownerEpoch(), errorCode.code(), errorMessage,
                LocalDateTime.now(), SystemActor.STEP_EXECUTION.code());
        if (!failed) {
            acknowledgeCancellation(claimed);
            return;
        }
        eventService.append(claimed.runId(), claimed.stepId(), CreativeEventType.STEP_FAILED,
                CreativeEventSources.RUNTIME, null, null);
        aggregationService.aggregate(claimed.runId());
    }

    /** 当前 owner 在观察到取消请求后确认取消，并触发 Run 聚合。 */
    @Transactional
    public void acknowledgeCancellation(ClaimedCreativeStep claimed) {
        runRepository.lockByRunId(claimed.runId());
        boolean cancelled = stepRepository.cancelOwned(claimed.stepId(), claimed.ownerInstanceId(),
                claimed.ownerEpoch(), LocalDateTime.now(), SystemActor.STEP_EXECUTION.code());
        if (cancelled) {
            aggregationService.aggregate(claimed.runId());
        }
    }
}
