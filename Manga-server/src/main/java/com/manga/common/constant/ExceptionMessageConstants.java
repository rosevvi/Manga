package com.manga.common.constant;

/**
 * 统一维护服务端技术异常的稳定描述和动态模板。
 */
public final class ExceptionMessageConstants {

    /** 用户主键回填失败提示。 */
    public static final String GENERATED_USER_ID_UNAVAILABLE = "Failed to obtain the generated user id";
    /** 外部登录会话过期时间无效提示。 */
    public static final String EXTERNAL_LOGIN_EXPIRATION_INVALID =
            "External login session expiration must be in the future";
    /** 初始管理员密码缺失提示。 */
    public static final String INITIAL_ADMIN_PASSWORD_REQUIRED =
            "MANGA_INITIAL_ADMIN_PASSWORD is required when the initial administrator does not exist";
    /** 安全算法不可用异常模板。 */
    public static final String ALGORITHM_UNAVAILABLE_TEMPLATE = "%s algorithm is unavailable";
    /** JWT 签名密钥长度不足提示模板。 */
    public static final String JWT_SECRET_TOO_SHORT_TEMPLATE =
            "MANGA_JWT_SECRET must contain at least %d bytes";
    /** JWT 主体缺失提示。 */
    public static final String JWT_SUBJECT_REQUIRED = "JWT subject is required";
    /** 微信二维码创建失败提示。 */
    public static final String WECHAT_QR_CODE_CREATION_FAILED = "Failed to create WeChat QR code";
    /** 微信 access_token 有效期过短提示。 */
    public static final String WECHAT_ACCESS_TOKEN_EXPIRATION_TOO_SHORT =
            "WeChat access token expiration is too short";
    /** 微信 access_token 获取失败提示。 */
    public static final String WECHAT_ACCESS_TOKEN_OBTAIN_FAILED = "Failed to obtain WeChat access token";
    /** 微信公众号配置不完整提示。 */
    public static final String WECHAT_CONFIGURATION_INCOMPLETE =
            "WeChat official account configuration is incomplete";
    /** 微信公众号接口操作失败模板。 */
    public static final String WECHAT_API_OPERATION_FAILED_TEMPLATE =
            "WeChat API could not %s, errorCode=%s, errorMessage=%s";
    /** 微信公众号创建二维码操作名称。 */
    public static final String WECHAT_CREATE_QR_CODE_OPERATION = "create temporary QR code";
    /** 微信公众号获取 access_token 操作名称。 */
    public static final String WECHAT_OBTAIN_ACCESS_TOKEN_OPERATION = "obtain access token";
    /** 微信响应反序列化失败提示。 */
    public static final String WECHAT_RESPONSE_DESERIALIZATION_FAILED = "Failed to deserialize WeChat API response";
    /** 微信回调正文无效提示。 */
    public static final String WECHAT_CALLBACK_PAYLOAD_INVALID = "Invalid WeChat callback payload";
    /** 项目主键回填失败提示。 */
    public static final String GENERATED_PROJECT_ID_UNAVAILABLE = "Failed to obtain the generated project id";
    /** 分镜主键回填失败提示。 */
    public static final String GENERATED_STORYBOARD_SHOT_ID_UNAVAILABLE =
            "Failed to obtain the generated storyboard shot id";
    /** AI 服务配置主键回填失败提示。 */
    public static final String GENERATED_AI_PROVIDER_CONFIG_ID_UNAVAILABLE =
            "Failed to obtain the generated AI provider config id";
    /** AI 凭据加密失败提示。 */
    public static final String AI_SECRET_ENCRYPTION_FAILED = "Failed to encrypt AI provider secret";

    /** 禁止实例化常量类。 */
    private ExceptionMessageConstants() {
    }
}
