package com.manga.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.List;

/**
 * 绑定 Manga 认证和跨域相关配置。
 */
@ConfigurationProperties(prefix = "manga.security")
public record MangaSecurityProperties(
        List<String> allowedOrigins,
        Duration corsMaxAge,
        Jwt jwt,
        InitialAdmin initialAdmin
) {

    /**
     * 保存 JWT 签发、校验及有效期配置。
     */
    public record Jwt(String secret, String issuer, Duration accessTokenTtl, Duration guestTokenTtl) {
    }

    /**
     * 保存首次建库时创建管理员所需的信息。
     */
    public record InitialAdmin(String username, String password, String displayName) {
    }
}
