package com.manga.integration.wechat;

/**
 * 表示微信公众号接口不可用或返回业务错误。
 */
public class WechatApiException extends RuntimeException {

    /** 构造微信公众号接口调用异常。 */
    public WechatApiException(String message) {
        super(message);
    }

    /** 构造微信公众号接口调用异常。 */
    public WechatApiException(String message, Throwable cause) {
        super(message, cause);
    }
}
