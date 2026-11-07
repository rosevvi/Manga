package com.manga.agent.application;

import com.manga.agent.domain.CreativeErrorCode;
import com.manga.agent.domain.CreativeEventType;
import com.manga.agent.domain.CreativeRunStatus;
import com.manga.agent.domain.CreativeRuntimeMessages;
import com.manga.agent.domain.CreativeStepStatus;
import com.manga.agent.domain.SystemActor;
import com.manga.entity.CreativeRun;
import com.manga.entity.CreativeStep;
import com.manga.repository.CreativeRunRepository;
import com.manga.repository.CreativeStepRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * 根据全部 Step 的持久化状态收敛 Run 聚合状态和唯一终态事件。
 */
@Service
@RequiredArgsConstructor
public class CreativeRunAggregationService {

    private final CreativeRunRepository runRepository;
    private final CreativeStepRepository stepRepository;
    private final CreativeEventService eventService;

    /**
     * 当 Run 不再存在非终态 Step 时计算终态。Run 行锁和 terminalSequence 保证并发完成的
     * Step 只会产生一个 Run 终态事件。
     */
    @Transactional
    public void aggregate(String runId) {
        CreativeRun run = runRepository.lockByRunId(runId);
        if (Objects.isNull(run) || run.getStatus().isTerminal()) return;
        if (stepRepository.countNonTerminal(runId) > 0) {
            if (run.getStatus() == CreativeRunStatus.CANCEL_REQUESTED || run.getStatus() == CreativeRunStatus.QUEUED) return;
            boolean executable = stepRepository.findByRunId(runId).stream().anyMatch(step ->
                    step.getStatus() == CreativeStepStatus.RUNNING || step.getStatus() == CreativeStepStatus.READY
                    || step.getStatus() == CreativeStepStatus.CANCEL_REQUESTED);
            CreativeRunStatus status = executable ? CreativeRunStatus.ACTIVE : CreativeRunStatus.WAITING;
            if (run.getStatus() != status) {
                run.setStatus(status); run.setUpdatedBy(SystemActor.CREATIVE_RUNTIME.code()); runRepository.update(run);
            }
            return;
        }
        CreativeRunStatus terminalStatus;
        CreativeEventType terminalEvent;
        if (run.getStatus() == CreativeRunStatus.CANCEL_REQUESTED) {
            terminalStatus = CreativeRunStatus.CANCELLED;
            terminalEvent = CreativeEventType.RUN_CANCELLED;
        } else if (stepRepository.countFailed(runId) > 0) {
            terminalStatus = CreativeRunStatus.FAILED;
            terminalEvent = CreativeEventType.RUN_FAILED;
            CreativeStep failed = stepRepository.findByRunId(runId).stream()
                    .filter(step -> step.getStatus() == CreativeStepStatus.FAILED).findFirst().orElse(null);
            run.setErrorCode(Objects.isNull(failed) ? CreativeErrorCode.CAPABILITY_EXECUTION_FAILED.code() : failed.getErrorCode());
            run.setErrorMessage(Objects.isNull(failed) ? CreativeRuntimeMessages.AGENT_STEP_EXECUTION_FAILED : failed.getErrorMessage());
        } else if (stepRepository.countCancelled(runId) > 0) {
            terminalStatus = CreativeRunStatus.CANCELLED;
            terminalEvent = CreativeEventType.RUN_CANCELLED;
        } else {
            terminalStatus = CreativeRunStatus.COMPLETED;
            terminalEvent = CreativeEventType.RUN_COMPLETED;
        }
        run.setStatus(terminalStatus);
        run.setActiveConversationId(null);
        run.setLeaseUntil(null);
        run.setFinishedAt(LocalDateTime.now());
        run.setUpdatedBy(SystemActor.CREATIVE_RUNTIME.code());
        runRepository.update(run);
        eventService.append(runId, terminalEvent, null);
    }
}
