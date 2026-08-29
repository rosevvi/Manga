package com.manga.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * 绑定微信公众号扫码登录及事件回调配置。
 */
@ConfigurationProperties(prefix = "wx.gzh")
public record WechatOfficialAccountProperties(
        String appId,
        String secret,
        String token,
        String apiBaseUrl,
        String qrCodeBaseUrl,
        String scenePrefix,
        Duration qrLoginTtl,
        Duration pollInterval
) {
}
