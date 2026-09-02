package com.manga.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.List;

/**
 * 绑定 Manga 认证和跨域相关配置。
 */
@ConfigurationProperties(prefix = "manga.security")
public record MangaSecurityProperties(
        /** 允许跨域访问的前端来源。 */
        List<String> allowedOrigins,
        /** 跨域预检结果缓存时长。 */
        Duration corsMaxAge,
        /** JWT 签发与有效期配置。 */
        Jwt jwt,
        /** 首次初始化管理员配置。 */
        InitialAdmin initialAdmin
) {

    /**
     * 保存 JWT 签发、校验及有效期配置。
     */
    public record Jwt(
            /** JWT 签名密钥。 */
            String secret,
            /** JWT 签发方。 */
            String issuer,
            /** 正式用户访问令牌有效期。 */
            Duration accessTokenTtl,
            /** 游客访问令牌有效期。 */
            Duration guestTokenTtl
    ) {
    }

    /**
     * 保存首次建库时创建管理员所需的信息。
     */
    public record InitialAdmin(
            /** 初始管理员用户名。 */
            String username,
            /** 初始管理员密码。 */
            String password,
            /** 初始管理员显示名称。 */
            String displayName
    ) {
    }
}
