package com.manga.agent.config;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;
import java.time.Duration;
import java.util.Objects;

/** 模型步骤使用独立有界线程池，避免长推理阻塞通用业务任务。 */
@Validated
@ConfigurationProperties(prefix = "manga.creative.dispatch-pool")
public record CreativeDispatchPoolProperties(
        /** 常驻调度线程数。 */ @DefaultValue("4") @Min(1) int corePoolSize,
        /** 执行线程上限，需结合模型并发与 Kernel 容量调整。 */ @DefaultValue("8") @Min(1) int maxPoolSize,
        /** 有界任务队列长度，超过后拒绝，由 Outbox 补投。 */ @DefaultValue("128") @Min(1) int queueCapacity,
        /** 应用关闭时等待已执行任务释放资源的最长时间。 */ @DefaultValue("30s") @NotNull Duration awaitTermination
) {
    /** 拒绝无法启动的线程池尺寸或非正关闭等待时长。 */
    @AssertTrue
    public boolean isConfigurationValid() {
        return maxPoolSize >= corePoolSize && Objects.nonNull(awaitTermination)
                && !awaitTermination.isNegative() && !awaitTermination.isZero();
    }
}
