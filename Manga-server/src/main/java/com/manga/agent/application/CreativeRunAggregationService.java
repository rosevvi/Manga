package com.manga.agent.application;

import com.manga.agent.domain.CreativeErrorCode;
import com.manga.agent.domain.CreativeEventType;
import com.manga.agent.domain.CreativeRunStatus;
import com.manga.agent.domain.SystemActor;
import com.manga.entity.CreativeRun;
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
        if (Objects.isNull(run) || run.getStatus().isTerminal() || stepRepository.countNonTerminal(runId) > 0) {
            return;
        }
        CreativeRunStatus terminalStatus;
        CreativeEventType terminalEvent;
        if (run.getStatus() == CreativeRunStatus.CANCEL_REQUESTED
                || stepRepository.countCancelled(runId) > 0) {
            terminalStatus = CreativeRunStatus.CANCELLED;
            terminalEvent = CreativeEventType.RUN_CANCELLED;
        } else if (stepRepository.countFailed(runId) > 0) {
            terminalStatus = CreativeRunStatus.FAILED;
            terminalEvent = CreativeEventType.RUN_FAILED;
            run.setErrorCode(CreativeErrorCode.CAPABILITY_EXECUTION_FAILED.code());
            run.setErrorMessage("Creative step execution failed");
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
