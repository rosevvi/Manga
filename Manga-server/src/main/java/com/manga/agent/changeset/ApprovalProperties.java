package com.manga.agent.changeset;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;
import java.util.Objects;

/** 审批有效期及补偿扫描上限，不占用执行租约。 */
@Validated
@ConfigurationProperties("manga.creative.approval")
public record ApprovalProperties(
        /** 从开放审批开始计算的有效期，必须大于零。 */ @NotNull @DefaultValue("24h") Duration ttl,
        /** 单次过期补偿批量，避免长事务扫描。 */ @Min(1) @Max(500) @DefaultValue("100") int batchSize
) {
    /** 拒绝无法等待的无效审批时长。 */
    @AssertTrue
    public boolean isTtlValid() { return Objects.nonNull(ttl) && !ttl.isNegative() && !ttl.isZero(); }
}
