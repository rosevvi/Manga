package com.manga.agent.execution;

import com.manga.agent.application.CreativeEventService;
import com.manga.agent.config.CreativeExecutionProperties;
import com.manga.agent.domain.CreativeEventType;
import com.manga.agent.domain.CreativeRunStatus;
import com.manga.agent.domain.SystemActor;
import com.manga.agent.event.CreativeEventSources;
import com.manga.agent.event.StepStartedEventPayload;
import com.manga.entity.CreativeRun;
import com.manga.entity.CreativeStep;
import com.manga.repository.CreativeRunRepository;
import com.manga.repository.CreativeStepRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.Optional;

/**
 * 原子领取 Step 并在同一事务内推进 Run 开始状态和开始事件。
 */
@Service
@RequiredArgsConstructor
public class CreativeStepClaimService {

    private final CreativeStepRepository stepRepository;
    private final CreativeRunRepository runRepository;
    private final CreativeEventService eventService;
    private final CreativeRuntimeInstanceIdentity instanceIdentity;
    private final CreativeExecutionProperties properties;

    /**
     * 仅当 Step 仍为相同 scheduleVersion 的 READY 状态时取得执行权。owner epoch 的递增与
     * `READY -> RUNNING` 在一条 SQL 中完成，失败表示信号重复或执行权已被其他实例取得。
     */
    @Transactional
    public Optional<ClaimedCreativeStep> claim(String stepId, long scheduleVersion) {
        CreativeStep candidate = stepRepository.findByStepId(stepId).orElse(null);
        if (Objects.isNull(candidate)) {
            return Optional.empty();
        }
        CreativeRun run = runRepository.lockByRunId(candidate.getRunId());
        if (Objects.isNull(run) || run.getStatus().isTerminal()
                || run.getStatus() == CreativeRunStatus.CANCEL_REQUESTED) {
            return Optional.empty();
        }
        String owner = instanceIdentity.value();
        String actor = SystemActor.STEP_EXECUTION.code();
        boolean claimed = stepRepository.claimReady(stepId, scheduleVersion, owner,
                LocalDateTime.now().plus(properties.stepLease()), actor);
        if (!claimed) {
            return Optional.empty();
        }
        CreativeStep step = stepRepository.findByStepId(stepId).orElseThrow();
        if (run.getStatus() == CreativeRunStatus.QUEUED || run.getStatus() == CreativeRunStatus.WAITING) {
            run.setStatus(CreativeRunStatus.ACTIVE);
            run.setStartedAt(Objects.isNull(run.getStartedAt()) ? LocalDateTime.now() : run.getStartedAt());
            run.setUpdatedBy(actor);
            runRepository.update(run);
            eventService.append(run.getRunId(), CreativeEventType.RUN_STARTED, null);
        }
        eventService.append(step.getRunId(), step.getStepId(), CreativeEventType.STEP_STARTED,
                CreativeEventSources.RUNTIME, null,
                new StepStartedEventPayload(step.getStepId(), step.getOwnerEpoch(), step.getAttemptNo()));
        return Optional.of(toClaim(step, run));
    }

    /** 查询当前实例仍持有的执行权，供执行异常后的围栏失败提交使用。 */
    @Transactional(readOnly = true)
    public Optional<ClaimedCreativeStep> findOwnedClaim(String stepId) {
        CreativeStep step = stepRepository.findByStepId(stepId)
                .filter(candidate -> instanceIdentity.value().equals(candidate.getOwnerInstanceId()))
                .filter(candidate -> candidate.getStatus() == com.manga.agent.domain.CreativeStepStatus.RUNNING)
                .orElse(null);
        if (Objects.isNull(step)) {
            return Optional.empty();
        }
        CreativeRun run = runRepository.findByRunId(step.getRunId()).orElse(null);
        return Objects.isNull(run) ? Optional.empty() : Optional.of(toClaim(step, run));
    }

    /** 将已领取 Step 与所属 Run 的冻结上下文组合为执行权快照。 */
    private ClaimedCreativeStep toClaim(CreativeStep step, CreativeRun run) {
        return new ClaimedCreativeStep(
                step.getStepId(),
                step.getRunId(),
                step.getStepType(),
                step.getOwnerInstanceId(),
                step.getOwnerEpoch(),
                step.getInputJson(),
                run.getUserId(),
                run.getProjectId(),
                run.getConversationId(),
                run.getDeadlineAt(),
                run.getPermissionPolicy(),
                step.getBlueprintSnapshotJson(),
                step.getBlueprintFingerprint(),
                step.getStateSessionId());
    }
}
