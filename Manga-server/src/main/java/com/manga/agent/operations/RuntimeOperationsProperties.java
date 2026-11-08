package com.manga.agent.operations;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.DecimalMin;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;

/** 新调用采用的容量、规划配额和用量定价；单次调用在受理时冻结费率。 */
@Validated
@ConfigurationProperties("manga.creative.operations")
public record RuntimeOperationsProperties(
        /** 每个模型配置跨实例允许的并发调用数。 */ @DefaultValue("4") @Min(1) @Max(64) int maxConcurrent,
        /** 每个模型配置每分钟最多受理次数。 */ @DefaultValue("60") @Min(1) int requestsPerMinute,
        /** 连续可重试故障达到此值后打开熔断。 */ @DefaultValue("3") @Min(1) int failureThreshold,
        /** 熔断冷却时长。 */ @DefaultValue("30s") Duration circuitCooldown,
        /** 每次调用的最长时间，过期按用量未知处理。 */ @DefaultValue("2m") Duration callTimeout,
        /** 每次调用保守预留费用，USD；不代表供应商结算额。 */ @DefaultValue("0.05") @DecimalMin("0.000001") BigDecimal reservedUsdPerCall,
        /** 每配置每日 UTC 规划预留额，失败不退款。 */ @DefaultValue("10") @DecimalMin("0.01") BigDecimal dailyReservedUsd,
        /** 仅精确模型名匹配，未配置时记录为未定价，不冒充零费用。 */ @DefaultValue @Valid Map<String, TokenPrice> prices,
        /** 终态 Run 超过此时间可创建无损审计归档。 */ @DefaultValue("30d") Duration archiveAfter,
        /** 单次归档允许的最大行数，超限需离线备份。 */ @DefaultValue("10000") @Min(1) @Max(100000) int archiveMaxRows
) {
    /** 空配置统一为空表；负时长会破坏租约与归档边界，启动时拒绝。 */
    public RuntimeOperationsProperties {
        prices = Objects.isNull(prices) ? Map.of() : Map.copyOf(prices);
        if (circuitCooldown.isNegative() || circuitCooldown.isZero() || callTimeout.isNegative() || callTimeout.isZero()
                || archiveAfter.isNegative() || archiveAfter.isZero()) throw new IllegalArgumentException(RuntimeOperationsConstants.INVALID_DURATION);
    }
    /** 按百万 token 计价，cached 是输入 token 的子集，reasoning 不再重复计入输出。 */
    public record TokenPrice(
            /** 标准输入 USD/百万 token。 */ @NotNull @DecimalMin("0") BigDecimal input,
            /** 缓存命中输入 USD/百万 token。 */ @NotNull @DecimalMin("0") BigDecimal cachedInput,
            /** 总输出 USD/百万 token。 */ @NotNull @DecimalMin("0") BigDecimal output
    ) { }
}
