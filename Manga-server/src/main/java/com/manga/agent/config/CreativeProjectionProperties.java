package com.manga.agent.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;
import java.time.Duration;
import jakarta.validation.constraints.AssertTrue;
import java.util.Objects;

/** 消息投影使用独立有界资源，饱和只延迟投影，不取消生成。 */
@Validated
@ConfigurationProperties(prefix = "manga.creative.projection")
public record CreativeProjectionProperties(
        /** 专用底层线程数上限。 */ @Min(1) @DefaultValue("2") int maxThreads,
        /** 每个底层线程的最大排队调度任务数。 */ @Min(1) @DefaultValue("256") int queueCapacity,
        /** 检查点领取租约时长。 */ @NotNull @DefaultValue("30s") Duration lease
) {
    /** 无效租约在启动时拒绝，不能让多个实例同时取得投影批次。 */
    @AssertTrue(message = "消息投影租约必须为正")
    public boolean isLeaseValid() {
        return Objects.nonNull(lease) && !lease.isNegative() && !lease.isZero();
    }
}
