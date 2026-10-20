package com.manga.agent.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * 绑定 Step 执行、租约、Dispatch Outbox 和恢复扫描参数。
 */
@Validated
@ConfigurationProperties(prefix = "manga.creative.execution")
public record CreativeExecutionProperties(
        /** Step 被成功领取后持有执行权的时长。 */
        @NotNull Duration stepLease,
        /** Dispatch Outbox 单次领取的有效时长。 */
        @NotNull Duration outboxClaimLease,
        /** Dispatch Outbox 首次失败后的退避时长。 */
        @NotNull Duration outboxRetryBase,
        /** Dispatch Outbox 指数退避允许达到的最大时长。 */
        @NotNull Duration outboxRetryMax,
        /** Fake 执行器两次增量事件之间的间隔。 */
        @NotNull Duration fakeDeltaInterval,
        /** 单轮 Dispatch Outbox 或恢复扫描的最大记录数。 */
        @Min(1) int batchSize
) {
}
