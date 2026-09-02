package com.manga.integration.wechat;

/**
 * 保存微信返回的临时二维码票据和展示地址。
 */
public record WechatQrCode(
        /** 微信公众号二维码票据。 */
        String ticket,
        /** 二维码访问地址。 */
        String qrCodeUrl,
        /** 剩余有效期，单位为秒。 */
        long expiresIn
) {
}
