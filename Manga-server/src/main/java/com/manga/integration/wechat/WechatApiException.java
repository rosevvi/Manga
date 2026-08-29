package com.manga.integration.wechat;

/**
 * 表示微信公众号接口不可用或返回业务错误。
 */
public class WechatApiException extends RuntimeException {

    public WechatApiException(String message) {
        super(message);
    }

    public WechatApiException(String message, Throwable cause) {
        super(message, cause);
    }
}
