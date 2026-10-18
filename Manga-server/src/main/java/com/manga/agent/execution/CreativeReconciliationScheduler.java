package com.manga.agent.execution;

import com.manga.agent.config.CreativeExecutionProperties;
import com.manga.agent.config.CreativeSchedulingConstants;
import com.manga.entity.CreativeStep;
import com.manga.repository.CreativeStepRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 周期性修复丢失调度信号和过期 Step 租约，不承担正常请求的首次调度。
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class CreativeReconciliationScheduler {

    private final CreativeStepRepository stepRepository;
    private final CreativeStepRecoveryService recoveryService;
    private final CreativeStepDispatcher stepDispatcher;
    private final CreativeExecutionProperties properties;

    /** 扫描有限批次异常状态并补发调度信号。 */
    @Scheduled(fixedDelayString = CreativeSchedulingConstants.RECONCILIATION_INTERVAL)
    public void reconcile() {
        LocalDateTime now = LocalDateTime.now();
        for (CreativeStep step : stepRepository.findExpired(now, properties.batchSize())) {
            recoveryService.recover(step, now)
                    .ifPresent(signal -> stepDispatcher.dispatchAsync(signal.stepId(), signal.scheduleVersion()));
        }
        for (CreativeStep step : stepRepository.findReady(properties.batchSize())) {
            stepDispatcher.dispatchAsync(step.getStepId(), step.getScheduleVersion());
        }
        log.debug("Creative reconciliation scan completed");
    }
}
