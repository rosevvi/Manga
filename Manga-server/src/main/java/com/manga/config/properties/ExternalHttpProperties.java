package com.manga.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * 绑定外部 HTTP 客户端的基础超时配置。
 */
@ConfigurationProperties(prefix = "manga.external-http")
public record ExternalHttpProperties(
        /** 连接超时时间。 */
        Duration connectTimeout,
        /** 请求超时时间。 */
        Duration requestTimeout
) {
}
