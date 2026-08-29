package com.manga.common.constant;

/**
 * 统一维护服务端技术异常的稳定描述和动态模板。
 */
public final class ExceptionMessageConstants {

    public static final String GENERATED_USER_ID_UNAVAILABLE = "Failed to obtain the generated user id";
    public static final String EXTERNAL_LOGIN_EXPIRATION_INVALID =
            "External login session expiration must be in the future";
    public static final String INITIAL_ADMIN_PASSWORD_REQUIRED =
            "MANGA_INITIAL_ADMIN_PASSWORD is required when the initial administrator does not exist";
    public static final String ALGORITHM_UNAVAILABLE_TEMPLATE = "%s algorithm is unavailable";
    public static final String JWT_SECRET_TOO_SHORT_TEMPLATE =
            "MANGA_JWT_SECRET must contain at least %d bytes";
    public static final String JWT_SUBJECT_REQUIRED = "JWT subject is required";
    public static final String WECHAT_QR_CODE_CREATION_FAILED = "Failed to create WeChat QR code";
    public static final String WECHAT_ACCESS_TOKEN_EXPIRATION_TOO_SHORT =
            "WeChat access token expiration is too short";
    public static final String WECHAT_ACCESS_TOKEN_OBTAIN_FAILED = "Failed to obtain WeChat access token";
    public static final String WECHAT_CONFIGURATION_INCOMPLETE =
            "WeChat official account configuration is incomplete";
    public static final String WECHAT_API_OPERATION_FAILED_TEMPLATE =
            "WeChat API could not %s, errorCode=%s, errorMessage=%s";
    public static final String WECHAT_CREATE_QR_CODE_OPERATION = "create temporary QR code";
    public static final String WECHAT_OBTAIN_ACCESS_TOKEN_OPERATION = "obtain access token";
    public static final String WECHAT_REQUEST_SERIALIZATION_FAILED = "Failed to serialize WeChat API request";
    public static final String WECHAT_CALLBACK_PAYLOAD_INVALID = "Invalid WeChat callback payload";
    public static final String GENERATED_PROJECT_ID_UNAVAILABLE = "Failed to obtain the generated project id";
    public static final String GENERATED_STORYBOARD_SHOT_ID_UNAVAILABLE =
            "Failed to obtain the generated storyboard shot id";
    public static final String GENERATED_AI_PROVIDER_CONFIG_ID_UNAVAILABLE =
            "Failed to obtain the generated AI provider config id";
    public static final String AI_SECRET_ENCRYPTION_FAILED = "Failed to encrypt AI provider secret";

    private ExceptionMessageConstants() {
    }
}
