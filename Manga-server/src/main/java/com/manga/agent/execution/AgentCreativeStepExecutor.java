package com.manga.agent.execution;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.manga.agent.blueprint.AgentBlueprint;
import com.manga.agent.blueprint.AgentBlueprintCodec;
import com.manga.agent.capability.AgentRuntimeContext;
import com.manga.agent.changeset.ChangeSetApprovalService;
import com.manga.agent.changeset.ChangeSetConstants;
import com.manga.agent.config.CreativeExecutionProperties;
import com.manga.agent.domain.CreativeErrorCode;
import com.manga.agent.domain.CreativeRuntimeMessages;
import com.manga.agent.domain.CreativeStepInput;
import com.manga.agent.domain.CreativeStepType;
import com.manga.agent.domain.CreativeToolStatus;
import com.manga.agent.harness.AgentRuntimeException;
import com.manga.agent.harness.MangaAgentInvoker;
import com.manga.common.constant.AsyncConstants;
import io.agentscope.core.event.AgentEvent;
import io.agentscope.core.event.AgentResultEvent;
import io.agentscope.core.event.ExceedMaxItersEvent;
import io.agentscope.core.event.TextBlockDeltaEvent;
import io.agentscope.core.event.ToolResultEndEvent;
import io.agentscope.core.event.ToolResultStartEvent;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.time.ZoneId;
import java.util.Objects;

/**
 * 使用 Step 中冻结的 Blueprint 执行 AgentScope，并将模型事件提交到 Creative Event Journal。
 */
@Component
public class AgentCreativeStepExecutor implements CreativeStepExecutor {

    private final AgentBlueprintCodec blueprintCodec;
    private final MangaAgentInvoker agentInvoker;
    private final CreativeStepLifecycleService lifecycleService;
    private final CreativeExecutionProperties executionProperties;
    private final TaskScheduler taskScheduler;
    private final ObjectMapper objectMapper;
    private final ChangeSetApprovalService approvals;

    public AgentCreativeStepExecutor(
            AgentBlueprintCodec blueprintCodec,
            MangaAgentInvoker agentInvoker,
            CreativeStepLifecycleService lifecycleService,
            CreativeExecutionProperties executionProperties,
            @Qualifier(AsyncConstants.MANGA_COMMON_TASK_SCHEDULER) TaskScheduler taskScheduler,
            ObjectMapper objectMapper,
            ChangeSetApprovalService approvals) {
        this.blueprintCodec = blueprintCodec;
        this.agentInvoker = agentInvoker;
        this.lifecycleService = lifecycleService;
        this.executionProperties = executionProperties;
        this.taskScheduler = taskScheduler;
        this.objectMapper = objectMapper;
        this.approvals = approvals;
    }

    /** 返回持久化 Agent 调用步骤类型。 */
    @Override
    public CreativeStepType stepType() {
        return CreativeStepType.AGENT_INVOCATION;
    }

    /** 执行模型与只读工具循环，持续续租并使用 owner epoch 围栏提交结果。 */
    @Override
    public void execute(ClaimedCreativeStep step) {
        AgentBlueprint blueprint = restoreBlueprint(step);
        // 上次执行可能在草稿落库后进程退出；此时直接开放审批，不再次调用模型。
        if (approvals.hasDraft(step.stepId()) && approvals.suspendIfProposed(step)) return;
        CreativeStepInput input = readInput(step.inputJson());
        AgentRuntimeContext context = runtimeContext(step);
        StringBuilder content = new StringBuilder();
        try (CreativeStepLeaseHeartbeat heartbeat = new CreativeStepLeaseHeartbeat(
                step, lifecycleService, executionProperties.stepLease(), taskScheduler)) {
            String instruction = input.instruction();
            if (Objects.nonNull(input.target())) instruction += ChangeSetConstants.TARGET_TEMPLATE
                    .formatted(input.target().resourceType(), input.target().resourceId());
            agentInvoker.invokeUntil(blueprint, context, approvals.instruction(step, instruction),
                            event -> event instanceof ToolResultEndEvent && approvals.hasDraft(step.stepId()))
                    .takeUntilOther(heartbeat.lossSignal())
                    .doOnNext(event -> commitEvent(step, event, content))
                    .blockLast();
        }
        if (approvals.suspendIfProposed(step)) return;
        if (!lifecycleService.canContinue(step)) {
            lifecycleService.acknowledgeCancellation(step);
            return;
        }
        lifecycleService.complete(step, write(new AgentStepResult(content.toString())));
    }

    /** 恢复 Blueprint，并复核 Step 独立保存的指纹列。 */
    private AgentBlueprint restoreBlueprint(ClaimedCreativeStep step) {
        if (!StringUtils.hasText(step.blueprintSnapshotJson())) {
            throw new AgentRuntimeException(
                    CreativeErrorCode.RUN_CONFIG_UNAVAILABLE,
                    CreativeRuntimeMessages.AGENT_STEP_BLUEPRINT_REQUIRED);
        }
        if (!StringUtils.hasText(step.stateSessionId())) {
            throw new AgentRuntimeException(
                    CreativeErrorCode.RUN_CONFIG_UNAVAILABLE,
                    CreativeRuntimeMessages.AGENT_STEP_STATE_SESSION_REQUIRED);
        }
        AgentBlueprint blueprint = blueprintCodec.deserialize(step.blueprintSnapshotJson());
        if (!Objects.equals(blueprint.fingerprint(), step.blueprintFingerprint())) {
            throw new AgentRuntimeException(
                    CreativeErrorCode.RUN_CONFIG_UNAVAILABLE,
                    CreativeRuntimeMessages.AGENT_BLUEPRINT_CONTRACT_MISMATCH);
        }
        return blueprint;
    }

    /** 构造不依赖线程本地变量的可信 Agent 调用上下文。 */
    private AgentRuntimeContext runtimeContext(ClaimedCreativeStep step) {
        if (Objects.isNull(step.deadlineAt())) {
            throw new AgentRuntimeException(
                    CreativeErrorCode.DEADLINE_EXCEEDED,
                    CreativeRuntimeMessages.AGENT_STEP_DEADLINE_EXCEEDED);
        }
        Instant deadline = step.deadlineAt().atZone(ZoneId.systemDefault()).toInstant();
        if (!deadline.isAfter(Instant.now())) {
            throw new AgentRuntimeException(
                    CreativeErrorCode.DEADLINE_EXCEEDED,
                    CreativeRuntimeMessages.AGENT_STEP_DEADLINE_EXCEEDED);
        }
        return new AgentRuntimeContext(
                step.userId(),
                step.projectId(),
                step.conversationId(),
                step.runId(),
                step.stepId(),
                step.stateSessionId(),
                step.ownerInstanceId(),
                step.ownerEpoch(),
                deadline,
                step.permissionPolicy());
    }

    /** 将 AgentScope 事件映射为稳定的 Manga 事件契约。 */
    private void commitEvent(ClaimedCreativeStep step, AgentEvent event, StringBuilder content) {
        boolean committed = true;
        if (event instanceof TextBlockDeltaEvent textEvent && StringUtils.hasText(textEvent.getDelta())) {
            content.append(textEvent.getDelta());
            committed = lifecycleService.appendDelta(step, textEvent.getDelta());
        } else if (event instanceof ToolResultStartEvent toolEvent) {
            committed = lifecycleService.appendToolStarted(
                    step, toolEvent.getToolCallId(), toolEvent.getToolCallName());
        } else if (event instanceof ToolResultEndEvent toolEvent) {
            committed = lifecycleService.appendToolCompleted(
                    step, toolEvent.getToolCallId(), toolEvent.getToolCallName(),
                    toolStatus(toolEvent));
        } else if (event instanceof AgentResultEvent resultEvent
                && content.isEmpty()
                && Objects.nonNull(resultEvent.getResult())
                && StringUtils.hasText(resultEvent.getResult().getTextContent())) {
            String result = resultEvent.getResult().getTextContent();
            content.append(result);
            committed = lifecycleService.appendDelta(step, result);
        } else if (event instanceof ExceedMaxItersEvent) {
            throw new AgentRuntimeException(
                    CreativeErrorCode.MODEL_INVOCATION_FAILED,
                    CreativeRuntimeMessages.AGENT_MAX_TURNS_EXCEEDED);
        }
        if (!committed) {
            throw new AgentRuntimeException(
                    CreativeErrorCode.OWNER_LEASE_LOST,
                    CreativeRuntimeMessages.AGENT_STEP_OWNER_LEASE_LOST);
        }
    }

    /** 将 AgentScope 工具终态收敛为 Manga 的稳定公开枚举。 */
    private CreativeToolStatus toolStatus(ToolResultEndEvent event) {
        return switch (event.getState()) {
            case SUCCESS -> CreativeToolStatus.SUCCESS;
            case ERROR -> CreativeToolStatus.ERROR;
            case INTERRUPTED -> CreativeToolStatus.INTERRUPTED;
            case DENIED -> CreativeToolStatus.DENIED;
            case RUNNING -> CreativeToolStatus.RUNNING;
        };
    }

    /** 解析受理时持久化的类型化输入。 */
    private CreativeStepInput readInput(String inputJson) {
        try {
            return objectMapper.readValue(inputJson, CreativeStepInput.class);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException(CreativeRuntimeMessages.EVENT_PAYLOAD_CORRUPTED, exception);
        }
    }

    /** 序列化最终文本，供 Step 结果审计和后续消息投影使用。 */
    private String write(AgentStepResult result) {
        try {
            return objectMapper.writeValueAsString(result);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException(
                    CreativeRuntimeMessages.AGENT_STEP_OUTPUT_SERIALIZATION_FAILED, exception);
        }
    }
}
