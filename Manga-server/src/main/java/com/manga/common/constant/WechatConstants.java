package com.manga.common.constant;

/**
 * 统一维护微信公众号协议字段和接口契约常量。
 */
public final class WechatConstants {

    public static final String ACCESS_TOKEN_PATH = "/cgi-bin/token";
    public static final String QR_CODE_CREATE_PATH = "/cgi-bin/qrcode/create";
    public static final String ACCESS_TOKEN_PARAMETER = "access_token";
    public static final String APP_ID_PARAMETER = "appid";
    public static final String SECRET_PARAMETER = "secret";
    public static final String GRANT_TYPE_PARAMETER = "grant_type";
    public static final String GRANT_TYPE = "client_credential";
    public static final String QR_ACTION_NAME = "QR_STR_SCENE";
    public static final String SIGNATURE_PARAMETER = "signature";
    public static final String TIMESTAMP_PARAMETER = "timestamp";
    public static final String NONCE_PARAMETER = "nonce";
    public static final String ECHO_STRING_PARAMETER = "echostr";
    public static final String XML_FROM_USER = "FromUserName";
    public static final String XML_MESSAGE_TYPE = "MsgType";
    public static final String XML_EVENT = "Event";
    public static final String XML_EVENT_KEY = "EventKey";
    public static final String MESSAGE_TYPE_EVENT = "event";
    public static final String EVENT_SUBSCRIBE = "subscribe";
    public static final String EVENT_SCAN = "SCAN";
    public static final String SUBSCRIBE_EVENT_KEY_PREFIX = "qrscene_";
    public static final String CALLBACK_SUCCESS_RESPONSE = "success";
    public static final String SHA_1_ALGORITHM = "SHA-1";
    public static final String SHA_256_ALGORITHM = "SHA-256";
    public static final String WECHAT_USERNAME_PREFIX = "wx_";
    public static final String WECHAT_DISPLAY_NAME_PREFIX = "微信用户";
    public static final int USERNAME_HASH_LENGTH = 24;
    public static final int DISPLAY_NAME_SUFFIX_LENGTH = 6;
    public static final int SCENE_KEY_MAX_LENGTH = 64;

    private WechatConstants() {
    }
}
