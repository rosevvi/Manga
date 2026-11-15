package com.manga.agent.config;

import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/** 独立配置创作事件缓冲和数据库写入调度资源，不占用通用业务队列。 */
@Validated
@ConfigurationProperties(prefix = "manga.creative.journal")
public record CreativeJournalProperties(
        /** 每个 Step 可预取的合并事件数；每项为一段正文或一个公开执行事件。 */
        @DefaultValue("4096") @Min(1) int eventQueueCapacity,
        /** Journal 专用 Scheduler 允许创建的最大底层线程数。 */
        @DefaultValue("16") @Min(1) int maxThreads,
        /** 每个底层线程允许排队的调度任务数，与单 Step 事件缓冲分开限制。 */
        @DefaultValue("2048") @Min(1) int schedulerQueueCapacity
) {
}
