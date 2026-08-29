package com.manga.dto;

/**
 * 返回公众号扫码登录所需的二维码和轮询参数。
 */
public record WechatQrLoginResponse(
        String loginToken,
        String qrCodeUrl,
        long expiresIn,
        long pollInterval
) {
}
