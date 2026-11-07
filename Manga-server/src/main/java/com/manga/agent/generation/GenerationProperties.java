package com.manga.agent.generation;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;
/** 外部生成治理参数；价格为运维配置的预估费用，不宣称供应商账单或扣费硬限额。 */
@Validated
@ConfigurationProperties("manga.creative.generation")
public record GenerationProperties(
    /** 媒体 worker 线程数，避免长图片请求挤占 Agent 和消息投影线程。 */ @Min(1) @Max(32) @DefaultValue("4") int workerThreads,
    /** 媒体 worker 等待队列；满时由 Job 扫描补偿。 */ @Min(1) @Max(1000) @DefaultValue("40") int workerQueueCapacity,
    /** 每批补偿数量。 */ @Min(1) @Max(100) @DefaultValue("20") int batchSize,
    /** 单供应商在途/未知作业上限。 */ @Min(1) @DefaultValue("4") int providerConcurrency,
    /** 单用户在同一供应商的在途/未知作业上限。 */ @Min(1) @DefaultValue("2") int userConcurrency,
    /** worker 执行权有效期，须超过网络超时的三倍。 */ @NotNull @DefaultValue("20m") Duration lease,
    /** 正常状态查询间隔。 */ @NotNull @DefaultValue("10s") Duration pollInterval,
    /** 网络故障最大退避。 */ @NotNull @DefaultValue("5m") Duration maxBackoff,
    /** 作业监测最长时间。 */ @NotNull @DefaultValue("24h") Duration maxAge,
    /** Fake 从提交到完成的持续时间。 */ @NotNull @DefaultValue("15s") Duration fakeDuration,
    /** 有界媒体文件大小，单位字节。 */ @Min(1024) @Max(33554432) @DefaultValue("20971520") int maxMediaBytes,
    /** 图片供应商调用超时，独立于短请求探活超时。 */ @NotNull @DefaultValue("5m") Duration httpTimeout,
    /** 单张预计费用，key 为允许使用的模型；不是供应商硬限额，空表示禁用付费生成。 */ Map<String,BigDecimal> imagePrices,
    /** 平台异步回调中继 HMAC 密钥；空字符串表示关闭，与 OpenAI Webhook 协议无关。 */ @DefaultValue("") String callbackSecret,
    /** 允许中继回调时钟偏移，防止签名长期重放。 */ @NotNull @DefaultValue("5m") Duration callbackClockSkew
) {
    /** 价格配置保持不可变，未配置时安全禁用真实供应商。 */
    public GenerationProperties {
        imagePrices = Map.copyOf(Objects.requireNonNullElse(imagePrices, Map.of()));
        callbackSecret = Objects.requireNonNullElse(callbackSecret, "");
    }
    /** 避免无效的 worker 时间和价格配置。 */
    @AssertTrue
    public boolean isValid() {
        return lease.compareTo(httpTimeout.multipliedBy(3)) > 0 && pollInterval.toMillis() > 0
            && maxBackoff.compareTo(pollInterval)>=0 && maxAge.compareTo(pollInterval)>0
            && httpTimeout.toMillis() > 0 && fakeDuration.toMillis() > 0
            && imagePrices.values().stream().allMatch(v -> v.signum()>0)
            && callbackClockSkew.toSeconds() > 0
            && (callbackSecret.isBlank() || callbackSecret.length() >= 32);
    }
    /** 配置摘要不能打印回调密钥。 */
    @Override
    public String toString() { return "GenerationProperties[workerThreads=" + workerThreads + ", callbackEnabled=" + !callbackSecret.isBlank() + "]"; }
}
