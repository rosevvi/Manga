package com.manga.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** 绑定章节导入任务的临时文件和重试配置。 */
@ConfigurationProperties(prefix = "manga.script.task")
public record ScriptTaskProperties(
        String inputDirectory,
        long maxInputSize,
        int maxAttempts
) {
}
