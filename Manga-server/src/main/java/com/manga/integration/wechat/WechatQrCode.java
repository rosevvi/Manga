package com.manga.integration.wechat;

/**
 * 保存微信返回的临时二维码票据和展示地址。
 */
public record WechatQrCode(
        String ticket,
        String qrCodeUrl,
        long expiresIn
) {
}
