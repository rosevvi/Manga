package com.manga.agent.harness;

import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/** 限制同一 Harness 中连续无进展的模型轮次，避免重复消耗。 */
@Validated
@ConfigurationProperties(prefix = "manga.creative.activity")
public record CreativeActivityProperties(
        /** 相同读取或错误且没有新结果时允许的连续模型轮次。 */
        @Min(1) @DefaultValue("3") int maxNoProgressTurns
) { }
