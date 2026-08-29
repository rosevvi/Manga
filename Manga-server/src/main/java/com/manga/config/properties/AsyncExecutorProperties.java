package com.manga.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * 绑定业务异步线程池的容量、存活时间和关闭等待配置。
 */
@ConfigurationProperties(prefix = "manga.async")
public record AsyncExecutorProperties(
        int corePoolSize,
        int maxPoolSize,
        int queueCapacity,
        Duration keepAlive,
        Duration awaitTermination,
        String threadNamePrefix
) {
}
