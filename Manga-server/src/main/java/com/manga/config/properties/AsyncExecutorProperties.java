package com.manga.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * 绑定 Manga 通用业务线程池的容量、存活时间和关闭等待配置。
 */
@ConfigurationProperties(prefix = "manga.executors.common")
public record AsyncExecutorProperties(
        /** 通用业务线程池核心线程数。 */
        int corePoolSize,
        /** 通用业务线程池最大线程数。 */
        int maxPoolSize,
        /** 通用业务线程池等待队列容量。 */
        int queueCapacity,
        /** 通用业务线程空闲存活时间。 */
        Duration keepAlive,
        /** 应用关闭时等待通用业务任务完成的时长。 */
        Duration awaitTermination,
        /** 通用业务线程名称前缀。 */
        String threadNamePrefix,
        /** 通用定时任务调度器线程数。 */
        int scheduledPoolSize,
        /** 通用定时任务调度器线程名称前缀。 */
        String scheduledThreadNamePrefix
) {
}
