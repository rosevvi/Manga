package com.manga.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * 绑定 Manga 业务 Redis 键空间与临时数据保留策略。
 */
@ConfigurationProperties(prefix = "manga.redis")
public record MangaRedisProperties(
        String keyPrefix,
        Duration externalLoginExpiredRetention
) {
}
