package com.manga.agent.collaboration;

import com.manga.agent.config.CreativeExecutionProperties;
import com.manga.agent.config.CreativeSchedulingConstants;
import com.manga.repository.CreativeRepairDecisionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import java.time.LocalDateTime;

/** 扫描到期修订确认单，每份单据通过独立事务恢复收敛。 */
@Component
@RequiredArgsConstructor
@Slf4j
public class CreativeRepairMaintenance {
    private final CreativeRepairDecisionRepository decisions;
    private final CreativeRepairDecisionService service;
    private final CreativeExecutionProperties properties;
    /** 单项故障保留检查点，不阻塞其他到期任务。 */
    @Scheduled(fixedDelayString = CreativeSchedulingConstants.RECONCILIATION_INTERVAL)
    public void expire() {
        for (var item : decisions.expired(LocalDateTime.now(), properties.batchSize())) {
            try {
                service.expire(item.getRunId(), item.getDecisionId());
            } catch (RuntimeException failure) {
                log.warn("Creative repair expiry deferred runId={} decisionId={}", item.getRunId(), item.getDecisionId(), failure);
            }
        }
    }
}
