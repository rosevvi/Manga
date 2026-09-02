package com.manga.dto;

/**
 * 返回登录令牌、有效期和当前身份信息。
 */
public record AuthResponse(
        /** 访问令牌类型。 */
        String tokenType,
        /** 访问令牌。 */
        String accessToken,
        /** 剩余有效期，单位为秒。 */
        long expiresIn,
        /** 当前认证用户信息。 */
        CurrentUserResponse user
) {
}
