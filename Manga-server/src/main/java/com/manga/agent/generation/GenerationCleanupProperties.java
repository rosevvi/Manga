package com.manga.agent.generation;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;
import java.time.Duration;
/** 无引用对象回收的等待窗口、互斥租约和退避配置。 */
@Validated
@ConfigurationProperties("manga.creative.generation.cleanup")
public record GenerationCleanupProperties(
        /** 单批回收上限。 */ @Min(1) @Max(100) @DefaultValue("20") int batchSize,
        /** 登记后至少等待该时长，且发布前仍复核 Job 执行权。 */ @NotNull @DefaultValue("24h") Duration grace,
        /** 跨实例回收租约。 */ @NotNull @DefaultValue("5m") Duration lease,
        /** 回收失败及活动对象的下次检查间隔。 */ @NotNull @DefaultValue("1h") Duration retryDelay,
        /** 删除墓碑复查间隔，处理极晚到的旧 worker 文件写入。 */ @NotNull @DefaultValue("24h") Duration tombstoneInterval
) {
    /** 拒绝零或负数时间，避免清理热循环。 */
    @AssertTrue
    public boolean isValid() { return grace.toMillis() > 0 && lease.toMillis() > 0 && retryDelay.toMillis() > 0 && tombstoneInterval.toMillis() > 0; }
}
