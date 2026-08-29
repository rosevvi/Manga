package com.manga.common.constant;

/**
 * 统一维护接口字段校验限制和提示。
 */
public final class ValidationConstants {

    public static final int USERNAME_MAX_LENGTH = 64;
    public static final int PASSWORD_MAX_LENGTH = 128;
    public static final int PASSWORD_MIN_LENGTH = 8;
    public static final int DISPLAY_NAME_MIN_LENGTH = 2;
    public static final int DISPLAY_NAME_MAX_LENGTH = 64;
    public static final String ROLE_CODE_PATTERN = "^[A-Z_]+$";

    public static final String USERNAME_REQUIRED = "用户名不能为空";
    public static final String USERNAME_TOO_LONG = "用户名长度不能超过 64 个字符";
    public static final String PASSWORD_REQUIRED = "密码不能为空";
    public static final String PASSWORD_TOO_LONG = "密码长度不能超过 128 个字符";
    public static final String NEW_PASSWORD_TOO_SHORT = "新密码长度不能少于 8 个字符";
    public static final String DISPLAY_NAME_REQUIRED = "显示名称不能为空";
    public static final String DISPLAY_NAME_LENGTH_INVALID = "显示名称长度需要在 2 到 64 个字符之间";
    public static final String ROLE_REQUIRED = "至少需要保留一个角色";
    public static final String ROLE_FORMAT_INVALID = "角色编码格式不正确";
    public static final int PROJECT_NAME_MAX_LENGTH = 120;
    public static final int PROJECT_DESCRIPTION_MAX_LENGTH = 1000;
    public static final int PROJECT_COVER_URL_MAX_LENGTH = 1024;
    public static final int PROJECT_GENRE_MAX_LENGTH = 64;
    public static final int SHOT_TITLE_MAX_LENGTH = 120;
    public static final int SHOT_SCENE_NAME_MAX_LENGTH = 120;
    public static final int SHOT_TYPE_MAX_LENGTH = 32;
    public static final int SHOT_CAMERA_MOVEMENT_MAX_LENGTH = 64;
    public static final int SHOT_CONTENT_MAX_LENGTH = 2000;
    public static final int SHOT_DIALOGUE_MAX_LENGTH = 2000;
    public static final int SHOT_SOUND_EFFECT_MAX_LENGTH = 500;
    public static final int SHOT_IMAGE_URL_MAX_LENGTH = 1024;
    public static final int SHOT_NOTES_MAX_LENGTH = 2000;
    public static final int SHOT_DURATION_MAX_SECONDS = 3600;
    public static final String PROJECT_NAME_REQUIRED = "项目名称不能为空";
    public static final String PROJECT_NAME_TOO_LONG = "项目名称不能超过 120 个字符";
    public static final String PROJECT_DESCRIPTION_TOO_LONG = "项目描述不能超过 1000 个字符";
    public static final String PROJECT_COVER_URL_TOO_LONG = "项目封面地址不能超过 1024 个字符";
    public static final String PROJECT_GENRE_TOO_LONG = "项目类型不能超过 64 个字符";
    public static final String SHOT_TITLE_REQUIRED = "分镜标题不能为空";
    public static final String SHOT_TITLE_TOO_LONG = "分镜标题不能超过 120 个字符";
    public static final String SHOT_SCENE_NAME_TOO_LONG = "场景名称不能超过 120 个字符";
    public static final String SHOT_TYPE_TOO_LONG = "景别不能超过 32 个字符";
    public static final String SHOT_CAMERA_MOVEMENT_TOO_LONG = "镜头运动不能超过 64 个字符";
    public static final String SHOT_DURATION_INVALID = "分镜时长需要在 0 到 3600 秒之间";
    public static final String SHOT_CONTENT_TOO_LONG = "画面内容不能超过 2000 个字符";
    public static final String SHOT_DIALOGUE_TOO_LONG = "对白不能超过 2000 个字符";
    public static final String SHOT_SOUND_EFFECT_TOO_LONG = "音效说明不能超过 500 个字符";
    public static final String SHOT_IMAGE_URL_TOO_LONG = "分镜图片地址不能超过 1024 个字符";
    public static final String SHOT_NOTES_TOO_LONG = "分镜备注不能超过 2000 个字符";
    public static final String SHOT_ORDER_REQUIRED = "分镜排序列表不能为空";
    public static final int AI_CONFIG_NAME_MAX_LENGTH = 80;
    public static final int AI_BASE_URL_MAX_LENGTH = 1024;
    public static final int AI_MODEL_MAX_LENGTH = 160;
    public static final int AI_API_KEY_MAX_LENGTH = 2048;
    public static final int AI_REMARK_MAX_LENGTH = 500;
    public static final String AI_CONFIG_NAME_REQUIRED = "配置名称不能为空";
    public static final String AI_CONFIG_NAME_TOO_LONG = "配置名称不能超过 80 个字符";
    public static final String AI_PROVIDER_REQUIRED = "AI 服务商不能为空";
    public static final String AI_BASE_URL_REQUIRED = "API Base URL 不能为空";
    public static final String AI_BASE_URL_TOO_LONG = "API Base URL 不能超过 1024 个字符";
    public static final String AI_BASE_URL_INVALID = "API Base URL 必须是有效的 HTTP 或 HTTPS 地址";
    public static final String AI_MODEL_TOO_LONG = "默认模型不能超过 160 个字符";
    public static final String AI_API_KEY_TOO_LONG = "API Key 不能超过 2048 个字符";
    public static final String AI_REMARK_TOO_LONG = "备注不能超过 500 个字符";

    private ValidationConstants() {
    }
}
