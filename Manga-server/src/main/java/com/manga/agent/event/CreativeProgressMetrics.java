package com.manga.agent.event;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/** 无任务标识标签的有界指标，区分事件排队、落库与消息投影延迟。 */
@Component
public class CreativeProgressMetrics {
    private final MeterRegistry registry;
    private final AtomicLong projectionBacklog = new AtomicLong();
    public CreativeProgressMetrics(MeterRegistry registry) {
        this.registry = registry;
        registry.gauge("manga.creative.projection.backlog.events", projectionBacklog);
    }
    /** 记录一条合并事件在 Journal 消费前的排队时间。 */
    public void queued(long nanos) { registry.timer("manga.creative.event.queue").record(Math.max(0, nanos), TimeUnit.NANOSECONDS); }
    /** 记录事务内实际落库耗时，不记录正文。 */
    public void persisted(long nanos) { registry.timer("manga.creative.event.persist").record(Math.max(0, nanos), TimeUnit.NANOSECONDS); }
    /** 记录最近一批投影落后事实水位的数量和事件年龄。 */
    public void projected(long backlog, Duration lag) {
        projectionBacklog.set(Math.max(0, backlog));
        registry.timer("manga.creative.projection.lag").record(lag.isNegative() ? Duration.ZERO : lag);
    }
}
