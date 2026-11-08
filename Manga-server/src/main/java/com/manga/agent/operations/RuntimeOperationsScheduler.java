package com.manga.agent.operations;

import com.manga.agent.domain.SystemActor;
import com.manga.repository.RuntimeOperationsRepository;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;
import jakarta.annotation.PostConstruct;

/** 故障对账、无损冷归档和有限标签告警；不自动重发任何供应商请求。 */
@Component
@RequiredArgsConstructor
@Slf4j
public class RuntimeOperationsScheduler {
    private final RuntimeOperationsRepository repository;
    private final ModelUsageService usage;
    private final CreativeAuditArchiveService archives;
    private final RuntimeOperationsProperties properties;
    private final MeterRegistry metrics;
    private long previousOpenCircuits = -1;
    private final AtomicLong openCircuits = new AtomicLong();

    /** Gauge 持有稳定引用，有限标签避免 Run 数量导致指标基数膨胀。 */
    @PostConstruct
    public void registerMetrics() { metrics.gauge(RuntimeOperationsConstants.OPEN_CIRCUITS_METRIC, openCircuits); }

    /** 定时任务仅做恢复；首次调用仍由 Model.stream 直接受理。 */
    @Scheduled(fixedDelayString = RuntimeOperationsConstants.SCAN_INTERVAL)
    public void reconcile() {
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        try {
            var stale = repository.stale(now);
            for (var call : stale) usage.finish(call, ModelCallStatus.UNKNOWN, null);
            if (!stale.isEmpty()) log.warn("Creative model calls marked unknown count={}", stale.size());
            long open = repository.gates().stream().filter(g -> Objects.nonNull(g.getOpenUntil()) && g.getOpenUntil().isAfter(now)).count();
            openCircuits.set(open);
            if (open != previousOpenCircuits) {
                if (open > 0) log.warn("Creative provider circuits open count={}", open);
                else log.info("Creative provider circuits recovered");
                previousOpenCircuits = open;
            }
            for (String runId : repository.archiveCandidates(LocalDateTime.now().minus(properties.archiveAfter()))) {
                try { archives.archive(runId, SystemActor.CREATIVE_RUNTIME.code()); }
                catch (RuntimeException failure) {
                    metrics.counter(RuntimeOperationsConstants.FAILURES_METRIC, "task", "archive").increment();
                    log.warn("Creative audit archive deferred runId={} failureType={}", runId, failure.getClass().getSimpleName());
                }
            }
        } catch (RuntimeException failure) {
            metrics.counter(RuntimeOperationsConstants.FAILURES_METRIC, "task", "reconciliation").increment();
            log.warn("Creative operations reconciliation deferred failureType={}", failure.getClass().getSimpleName());
        }
    }
}
