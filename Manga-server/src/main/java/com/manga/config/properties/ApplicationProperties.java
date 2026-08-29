package com.manga.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 绑定应用自身的基础信息配置。
 */
@ConfigurationProperties(prefix = "spring.application")
public record ApplicationProperties(String name) {
}
