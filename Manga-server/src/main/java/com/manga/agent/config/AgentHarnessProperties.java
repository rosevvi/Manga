package com.manga.agent.config;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * 绑定 Harness 隔离和状态会话租约参数。
 */
@Validated
@ConfigurationProperties(prefix = "manga.creative.agent.harness")
public record AgentHarnessProperties(
        /** 同一状态会话单次独占租约的有效期。 */
        @NotNull Duration sessionLease,
        /** 内存中允许同时缓存的最大 Kernel 数。 */
        @Min(1) int maxKernels,
        /** Kernel 容量用尽时允许等待空闲资源的最长时间。 */
        @NotNull Duration kernelAcquireTimeout,
        /** 应用关闭时等待活跃 KernelHandle 释放的最长时间。 */
        @NotNull Duration shutdownDrainTimeout
) {
}
