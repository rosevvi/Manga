package com.manga.agent.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * 绑定创作运行时写入 Run 和事件契约的稳定版本信息。
 */
@Validated
@ConfigurationProperties(prefix = "manga.creative.runtime")
public record CreativeRuntimeProperties(
        /** 当前创作引擎版本。 */
        @NotBlank String engineVersion,
        /** 当前工作流定义版本。 */
        @NotBlank String workflowVersion,
        /** 当前事件 Envelope Schema 版本。 */
        @Min(1) int eventSchemaVersion,
        /** 单次 Run 从受理到终止允许占用的最长时间。 */
        @NotNull Duration runTimeout
) {
}
