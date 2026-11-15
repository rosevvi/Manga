package com.manga.agent.application;

import com.manga.agent.event.CreativeEventsCommitted;
import com.manga.common.constant.AsyncConstants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import reactor.core.scheduler.Scheduler;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** 提交后立即触发按 Run 去重的批次投影，遗漏和拒绝由持久化水位扫描补偿。 */
@Component
@Slf4j
public class CreativeProjectionDispatcher {
    private final CreativeMessageProjector projector;
    private final Scheduler scheduler;
    private final Set<String> scheduled = ConcurrentHashMap.newKeySet();
    private final Set<String> dirty = ConcurrentHashMap.newKeySet();
    public CreativeProjectionDispatcher(CreativeMessageProjector projector,
            @Qualifier(AsyncConstants.MANGA_PROJECTION_SCHEDULER) Scheduler scheduler) {
        this.projector = projector; this.scheduler = scheduler;
    }
    /** 提交线程只投递信号，不等待数据库投影。 */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void committed(CreativeEventsCommitted event) { wake(event.runId()); }

    /** 每个 Run 只有一个本机任务；跨实例由独立检查点行锁和租约串行化。 */
    public void wake(String runId) {
        dirty.add(runId);
        if (!scheduled.add(runId)) {
            return;
        }
        try {
            scheduler.schedule(() -> {
                try {
                    do {
                        dirty.remove(runId);
                        while (projector.project(runId) > 0) { /* 每个批次由独立代理事务提交。 */ }
                    } while (dirty.remove(runId));
                } catch (RuntimeException failure) {
                    log.warn("Message projection deferred runId={} type={}", runId, failure.getClass().getSimpleName());
                } finally {
                    scheduled.remove(runId);
                    if (dirty.remove(runId)) { wake(runId); }
                }
            });
        } catch (RuntimeException rejected) {
            scheduled.remove(runId);
            dirty.remove(runId);
            log.debug("Message projection wakeup deferred runId={}", runId);
        }
    }
}
