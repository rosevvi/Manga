package com.manga.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * 绑定微信公众号扫码登录及事件回调配置。
 */
@ConfigurationProperties(prefix = "wx.gzh")
public record WechatOfficialAccountProperties(
        /** 微信公众号 AppID。 */
        String appId,
        /** 第三方或签名密钥。 */
        String secret,
        /** 微信公众号回调校验 Token。 */
        String token,
        /** 微信公众号 API 基础地址。 */
        String apiBaseUrl,
        /** 微信公众号二维码展示基础地址。 */
        String qrCodeBaseUrl,
        /** 微信扫码场景值前缀。 */
        String scenePrefix,
        /** 微信扫码登录会话有效期。 */
        Duration qrLoginTtl,
        /** 前端扫码状态轮询间隔。 */
        Duration pollInterval
) {
}
