package com.manga.agent.application;

import com.manga.agent.domain.CreativeActor;
import com.manga.agent.domain.CreativeEventType;
import com.manga.agent.domain.CreativeRunStateMachine;
import com.manga.agent.domain.CreativeRunStatus;
import com.manga.common.enums.CommonResponseCode;
import com.manga.common.exception.BusinessException;
import com.manga.entity.CreativeRun;
import com.manga.repository.CreativeRunRepository;
import com.manga.repository.CreativeStepRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 将用户取消请求传播到 Run 下的全部非终态 Step，并触发聚合收敛。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CreativeRunCancellationService {

    private final CreativeRunRepository runRepository;
    private final CreativeStepRepository stepRepository;
    private final CreativeEventService eventService;
    private final CreativeRunAggregationService aggregationService;

    /**
     * 幂等请求取消。未领取或等待中的 Step 立即取消，RUNNING Step 进入 CANCEL_REQUESTED，
     * 由当前 owner 确认或由租约过期对账收敛，避免绕过外部副作用清理。
     */
    @Transactional
    public void cancel(CancelCreativeRunCommand command) {
        CreativeRun run = runRepository.lockOwned(command.runId(), command.userId())
                .orElseThrow(() -> new BusinessException(CommonResponseCode.FORBIDDEN, HttpStatus.FORBIDDEN));
        if (run.getStatus().isTerminal()) {
            return;
        }
        String actor = CreativeActor.user(command.userId());
        LocalDateTime now = LocalDateTime.now();
        if (run.getStatus() != CreativeRunStatus.CANCEL_REQUESTED) {
            CreativeRunStateMachine.requireTransition(run.getStatus(), CreativeRunStatus.CANCEL_REQUESTED);
            run.setStatus(CreativeRunStatus.CANCEL_REQUESTED);
            run.setCancelRequestedAt(now);
            run.setActiveConversationId(null);
            run.setUpdatedBy(actor);
            runRepository.update(run);
            eventService.append(run.getRunId(), CreativeEventType.RUN_CANCEL_REQUESTED, null);
        }
        int cancelled = stepRepository.cancelWaitingByRunId(run.getRunId(), now, actor);
        int requested = stepRepository.requestCancelRunningByRunId(run.getRunId(), actor);
        aggregationService.aggregate(run.getRunId());
        log.info("Creative Run cancellation propagated runId={} userId={} cancelledSteps={} runningSteps={}",
                run.getRunId(), command.userId(), cancelled, requested);
    }
}
