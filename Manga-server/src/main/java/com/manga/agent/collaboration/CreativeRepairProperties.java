package com.manga.agent.collaboration;

import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;
import java.time.Duration;
import jakarta.validation.constraints.AssertTrue;
import java.util.Objects;

/** 用户修订确认的等待期限，等待不消耗原 Run 的执行时间。 */
@Validated
@ConfigurationProperties(prefix = "manga.creative.repair")
public record CreativeRepairProperties(
        /** 每次确认单的有效等待时长。 */ @NotNull @DefaultValue("24h") Duration confirmationTtl
) {
    /** 等待期限必须为正，防止配置错误导致新确认单立即过期。 */
    @AssertTrue(message = "修订确认等待期限必须为正")
    public boolean isTtlValid() {
        return Objects.nonNull(confirmationTtl) && !confirmationTtl.isNegative() && !confirmationTtl.isZero();
    }
}
