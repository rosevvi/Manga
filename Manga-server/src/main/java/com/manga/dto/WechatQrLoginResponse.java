package com.manga.dto;

/**
 * 返回公众号扫码登录所需的二维码和轮询参数。
 */
public record WechatQrLoginResponse(
        /** 外部登录会话令牌。 */
        String loginToken,
        /** 二维码访问地址。 */
        String qrCodeUrl,
        /** 剩余有效期，单位为秒。 */
        long expiresIn,
        /** 前端扫码状态轮询间隔。 */
        long pollInterval
) {
}
