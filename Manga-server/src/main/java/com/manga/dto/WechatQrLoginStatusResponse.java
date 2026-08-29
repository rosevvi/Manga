package com.manga.dto;

/**
 * 返回公众号扫码登录会话状态，并在首次确认时携带平台令牌。
 */
public record WechatQrLoginStatusResponse(
        String status,
        long expiresIn,
        AuthResponse authentication
) {
}
