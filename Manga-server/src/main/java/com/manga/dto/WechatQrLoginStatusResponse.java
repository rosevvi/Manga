package com.manga.dto;

/**
 * 返回公众号扫码登录会话状态，并在首次确认时携带平台令牌。
 */
public record WechatQrLoginStatusResponse(
        /** 微信扫码登录会话状态。 */
        String status,
        /** 剩余有效期，单位为秒。 */
        long expiresIn,
        /** 扫码登录成功后的认证信息。 */
        AuthResponse authentication
) {
}
