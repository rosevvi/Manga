package com.manga.integration.wechat;

/**
 * 保存微信公众号推送中与扫码登录有关的事件字段。
 */
public record WechatCallbackMessage(
        /** 微信公众号消息发送方 OpenID。 */
        String fromUserName,
        /** 微信公众号消息类型。 */
        String messageType,
        /** 微信公众号事件类型。 */
        String event,
        /** 微信公众号事件场景值。 */
        String eventKey
) {
}
