package com.manga.dto;

/**
 * 返回登录令牌、有效期和当前身份信息。
 */
public record AuthResponse(
        String tokenType,
        String accessToken,
        long expiresIn,
        CurrentUserResponse user
) {
}
