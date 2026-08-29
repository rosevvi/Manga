package com.manga.integration.wechat;

/**
 * 保存微信公众号推送中与扫码登录有关的事件字段。
 */
public record WechatCallbackMessage(
        String fromUserName,
        String messageType,
        String event,
        String eventKey
) {
}
