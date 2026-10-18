package com.manga.agent.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * 绑定单 Director 阶段的稳定 Profile 与模型上下文参数。
 */
@Validated
@ConfigurationProperties(prefix = "manga.creative.agent")
public record CreativeAgentProperties(
        /** Director 系统提示词的类路径资源。 */
        @NotBlank String directorPromptResource,
        /** 内置 Director 定义版本，语义变化时必须递增。 */
        @Min(1) long directorProfileVersion,
        /** Director 单次调用允许的最大推理轮次。 */
        @Min(1) int directorMaxTurns,
        /** Director 默认模型上下文窗口大小。 */
        @Min(1024) int contextWindow
) {
}
