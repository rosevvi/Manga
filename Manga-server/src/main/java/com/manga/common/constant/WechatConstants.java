package com.manga.common.constant;

/**
 * 统一维护微信公众号协议字段和接口契约常量。
 */
public final class WechatConstants {

    /** 微信 access_token 接口路径。 */
    public static final String ACCESS_TOKEN_PATH = "/cgi-bin/token";
    /** 微信二维码创建接口路径。 */
    public static final String QR_CODE_CREATE_PATH = "/cgi-bin/qrcode/create";
    /** 微信 access_token 参数名称。 */
    public static final String ACCESS_TOKEN_PARAMETER = "access_token";
    /** 微信公众号 AppID 参数名称。 */
    public static final String APP_ID_PARAMETER = "appid";
    /** 微信公众号 AppSecret 参数名称。 */
    public static final String SECRET_PARAMETER = "secret";
    /** 微信授权类型参数名称。 */
    public static final String GRANT_TYPE_PARAMETER = "grant_type";
    /** 微信 access_token 授权类型。 */
    public static final String GRANT_TYPE = "client_credential";
    /** 微信临时二维码动作名称。 */
    public static final String QR_ACTION_NAME = "QR_STR_SCENE";
    /** 微信回调签名参数名称。 */
    public static final String SIGNATURE_PARAMETER = "signature";
    /** 微信回调时间戳参数名称。 */
    public static final String TIMESTAMP_PARAMETER = "timestamp";
    /** 微信回调随机数参数名称。 */
    public static final String NONCE_PARAMETER = "nonce";
    /** 微信回调验证字符串参数名称。 */
    public static final String ECHO_STRING_PARAMETER = "echostr";
    /** 微信回调发送方 XML 节点名称。 */
    public static final String XML_FROM_USER = "FromUserName";
    /** 微信回调消息类型 XML 节点名称。 */
    public static final String XML_MESSAGE_TYPE = "MsgType";
    /** 微信回调事件 XML 节点名称。 */
    public static final String XML_EVENT = "Event";
    /** 微信回调场景值 XML 节点名称。 */
    public static final String XML_EVENT_KEY = "EventKey";
    /** 微信事件消息类型值。 */
    public static final String MESSAGE_TYPE_EVENT = "event";
    /** 微信关注事件类型值。 */
    public static final String EVENT_SUBSCRIBE = "subscribe";
    /** 微信扫码事件类型值。 */
    public static final String EVENT_SCAN = "SCAN";
    /** 微信关注事件场景值前缀。 */
    public static final String SUBSCRIBE_EVENT_KEY_PREFIX = "qrscene_";
    /** 微信回调成功响应正文。 */
    public static final String CALLBACK_SUCCESS_RESPONSE = "success";
    /** 微信签名使用的 SHA-1 算法名称。 */
    public static final String SHA_1_ALGORITHM = "SHA-1";
    /** 微信用户标识使用的 SHA-256 算法名称。 */
    public static final String SHA_256_ALGORITHM = "SHA-256";
    /** 微信用户默认用户名前缀。 */
    public static final String WECHAT_USERNAME_PREFIX = "wx_";
    /** 微信用户默认显示名称前缀。 */
    public static final String WECHAT_DISPLAY_NAME_PREFIX = "微信用户";
    /** 微信用户名哈希后缀长度。 */
    public static final int USERNAME_HASH_LENGTH = 24;
    /** 微信显示名称后缀长度。 */
    public static final int DISPLAY_NAME_SUFFIX_LENGTH = 6;
    /** 微信二维码场景值最大长度。 */
    public static final int SCENE_KEY_MAX_LENGTH = 64;

    /** 禁止实例化常量类。 */
    private WechatConstants() {
    }
}
