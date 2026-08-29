package com.manga.integration.wechat;

import java.time.Duration;

/**
 * 定义微信公众号服务端 API 的最小调用契约。
 */
public interface WechatOfficialAccountClient {

    /**
     * 为指定场景创建临时公众号二维码。
     */
    WechatQrCode createTemporaryQrCode(String sceneKey, Duration timeToLive);
}
