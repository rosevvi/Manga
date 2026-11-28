package com.manga.agent.operations;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;
import java.time.Duration;

/** 模型调用的并发、速率、时限及归档配置。 */
@Validated
@ConfigurationProperties("manga.creative.operations")
public record RuntimeOperationsProperties(
        /** 每个模型配置跨实例允许的并发调用数。 */ @DefaultValue("4") @Min(1) @Max(64) int maxConcurrent,
        /** 每个模型配置每分钟最多受理次数。 */ @DefaultValue("60") @Min(1) int requestsPerMinute,
        /** 连续可重试故障达到此值后打开熔断。 */ @DefaultValue("3") @Min(1) int failureThreshold,
        /** 熔断冷却时长。 */ @DefaultValue("30s") Duration circuitCooldown,
        /** 连续未收到供应商数据的时限。 */ @DefaultValue("2m") Duration idleTimeout,
        /** 终态 Run 超过此时间可创建无损审计归档。 */ @DefaultValue("30d") Duration archiveAfter,
        /** 单次归档允许的最大行数，超限需离线备份。 */ @DefaultValue("10000") @Min(1) @Max(100000) int archiveMaxRows
) {
    /** 校验模型调用与归档的时限。 */
    public RuntimeOperationsProperties {
        if (circuitCooldown.isNegative() || circuitCooldown.isZero()
                || idleTimeout.isNegative() || idleTimeout.isZero()
                || archiveAfter.isNegative() || archiveAfter.isZero()) throw new IllegalArgumentException(RuntimeOperationsConstants.INVALID_DURATION);
    }
}
