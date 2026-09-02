package com.manga.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * 绑定业务异步线程池的容量、存活时间和关闭等待配置。
 */
@ConfigurationProperties(prefix = "manga.async")
public record AsyncExecutorProperties(
        /** 异步线程池核心线程数。 */
        int corePoolSize,
        /** 异步线程池最大线程数。 */
        int maxPoolSize,
        /** 异步线程池等待队列容量。 */
        int queueCapacity,
        /** 异步线程空闲存活时间。 */
        Duration keepAlive,
        /** 应用关闭时等待异步任务完成的时长。 */
        Duration awaitTermination,
        /** 异步线程名称前缀。 */
        String threadNamePrefix
) {
}
