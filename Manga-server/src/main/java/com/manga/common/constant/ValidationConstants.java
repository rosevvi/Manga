package com.manga.common.constant;

/**
 * 统一维护接口字段校验限制和提示。
 */
public final class ValidationConstants {

    /** 用户名最大长度。 */
    public static final int USERNAME_MAX_LENGTH = 64;
    /** 密码最大长度。 */
    public static final int PASSWORD_MAX_LENGTH = 128;
    /** 密码最小长度。 */
    public static final int PASSWORD_MIN_LENGTH = 8;
    /** 显示名称最小长度。 */
    public static final int DISPLAY_NAME_MIN_LENGTH = 2;
    /** 显示名称最大长度。 */
    public static final int DISPLAY_NAME_MAX_LENGTH = 64;
    /** 角色编码合法格式。 */
    public static final String ROLE_CODE_PATTERN = "^[A-Z_]+$";

    /** 用户名必填校验提示。 */
    public static final String USERNAME_REQUIRED = "用户名不能为空";
    /** 用户名超出长度限制提示。 */
    public static final String USERNAME_TOO_LONG = "用户名长度不能超过 64 个字符";
    /** 密码必填校验提示。 */
    public static final String PASSWORD_REQUIRED = "密码不能为空";
    /** 密码超出长度限制提示。 */
    public static final String PASSWORD_TOO_LONG = "密码长度不能超过 128 个字符";
    /** 新密码长度不足提示。 */
    public static final String NEW_PASSWORD_TOO_SHORT = "新密码长度不能少于 8 个字符";
    /** 显示名称必填校验提示。 */
    public static final String DISPLAY_NAME_REQUIRED = "显示名称不能为空";
    /** 显示名称长度无效提示。 */
    public static final String DISPLAY_NAME_LENGTH_INVALID = "显示名称长度需要在 2 到 64 个字符之间";
    /** 角色必填校验提示。 */
    public static final String ROLE_REQUIRED = "至少需要保留一个角色";
    /** 角色格式无效提示。 */
    public static final String ROLE_FORMAT_INVALID = "角色编码格式不正确";
    /** 项目名称最大长度。 */
    public static final int PROJECT_NAME_MAX_LENGTH = 120;
    /** 项目简介最大长度。 */
    public static final int PROJECT_DESCRIPTION_MAX_LENGTH = 1000;
    /** 项目封面地址最大长度。 */
    public static final int PROJECT_COVER_URL_MAX_LENGTH = 1024;
    /** 项目作品类型最大长度。 */
    public static final int PROJECT_GENRE_MAX_LENGTH = 64;
    /** 项目画面比例最大长度。 */
    public static final int PROJECT_ASPECT_RATIO_MAX_LENGTH = 20;
    /** 项目画风最大长度。 */
    public static final int PROJECT_ART_STYLE_MAX_LENGTH = 64;
    /** 项目画风描述最大长度。 */
    public static final int PROJECT_ART_STYLE_DESCRIPTION_MAX_LENGTH = 2000;
    /** 项目画风图片生成提示词最大长度。 */
    public static final int PROJECT_ART_STYLE_IMAGE_PROMPT_MAX_LENGTH = 2000;
    /** 项目画风参考图地址最大长度。 */
    public static final int PROJECT_ART_STYLE_IMAGE_URL_MAX_LENGTH = 1024;
    /** 分镜标题最大长度。 */
    public static final int SHOT_TITLE_MAX_LENGTH = 120;
    /** 分镜场景名称最大长度。 */
    public static final int SHOT_SCENE_NAME_MAX_LENGTH = 120;
    /** 镜头类型最大长度。 */
    public static final int SHOT_TYPE_MAX_LENGTH = 32;
    /** 镜头运动方式最大长度。 */
    public static final int SHOT_CAMERA_MOVEMENT_MAX_LENGTH = 64;
    /** 分镜内容最大长度。 */
    public static final int SHOT_CONTENT_MAX_LENGTH = 2000;
    /** 分镜对白最大长度。 */
    public static final int SHOT_DIALOGUE_MAX_LENGTH = 2000;
    /** 分镜音效最大长度。 */
    public static final int SHOT_SOUND_EFFECT_MAX_LENGTH = 500;
    /** 分镜图片地址最大长度。 */
    public static final int SHOT_IMAGE_URL_MAX_LENGTH = 1024;
    /** 分镜备注最大长度。 */
    public static final int SHOT_NOTES_MAX_LENGTH = 2000;
    /** 镜头时长最大秒数。 */
    public static final int SHOT_DURATION_MAX_SECONDS = 3600;
    /** 项目名称必填校验提示。 */
    public static final String PROJECT_NAME_REQUIRED = "项目名称不能为空";
    /** 项目名称超出长度限制提示。 */
    public static final String PROJECT_NAME_TOO_LONG = "项目名称不能超过 120 个字符";
    /** 项目简介超出长度限制提示。 */
    public static final String PROJECT_DESCRIPTION_TOO_LONG = "项目描述不能超过 1000 个字符";
    /** 项目封面地址超出长度限制提示。 */
    public static final String PROJECT_COVER_URL_TOO_LONG = "项目封面地址不能超过 1024 个字符";
    /** 项目作品类型超出长度限制提示。 */
    public static final String PROJECT_GENRE_TOO_LONG = "项目类型不能超过 64 个字符";
    /** 项目画面比例超出长度限制提示。 */
    public static final String PROJECT_ASPECT_RATIO_TOO_LONG = "项目画面比例不能超过 20 个字符";
    /** 项目画风超出长度限制提示。 */
    public static final String PROJECT_ART_STYLE_TOO_LONG = "项目画风不能超过 64 个字符";
    /** 项目画风描述超出长度限制提示。 */
    public static final String PROJECT_ART_STYLE_DESCRIPTION_TOO_LONG = "项目画风描述不能超过 2000 个字符";
    /** 项目画风图片生成提示词超出长度限制提示。 */
    public static final String PROJECT_ART_STYLE_IMAGE_PROMPT_TOO_LONG = "项目画风提示词不能超过 2000 个字符";
    /** 项目画风参考图地址超出长度限制提示。 */
    public static final String PROJECT_ART_STYLE_IMAGE_URL_TOO_LONG = "项目画风参考图地址不能超过 1024 个字符";
    /** 分镜标题必填校验提示。 */
    public static final String SHOT_TITLE_REQUIRED = "分镜标题不能为空";
    /** 分镜标题超出长度限制提示。 */
    public static final String SHOT_TITLE_TOO_LONG = "分镜标题不能超过 120 个字符";
    /** 分镜场景名称超出长度限制提示。 */
    public static final String SHOT_SCENE_NAME_TOO_LONG = "场景名称不能超过 120 个字符";
    /** 镜头类型超出长度限制提示。 */
    public static final String SHOT_TYPE_TOO_LONG = "景别不能超过 32 个字符";
    /** 镜头运动方式超出长度限制提示。 */
    public static final String SHOT_CAMERA_MOVEMENT_TOO_LONG = "镜头运动不能超过 64 个字符";
    /** 镜头时长格式或取值无效提示。 */
    public static final String SHOT_DURATION_INVALID = "分镜时长需要在 0 到 3600 秒之间";
    /** 分镜内容超出长度限制提示。 */
    public static final String SHOT_CONTENT_TOO_LONG = "画面内容不能超过 2000 个字符";
    /** 分镜对白超出长度限制提示。 */
    public static final String SHOT_DIALOGUE_TOO_LONG = "对白不能超过 2000 个字符";
    /** 分镜音效超出长度限制提示。 */
    public static final String SHOT_SOUND_EFFECT_TOO_LONG = "音效说明不能超过 500 个字符";
    /** 分镜图片地址超出长度限制提示。 */
    public static final String SHOT_IMAGE_URL_TOO_LONG = "分镜图片地址不能超过 1024 个字符";
    /** 分镜备注超出长度限制提示。 */
    public static final String SHOT_NOTES_TOO_LONG = "分镜备注不能超过 2000 个字符";
    /** 分镜排序必填校验提示。 */
    public static final String SHOT_ORDER_REQUIRED = "分镜排序列表不能为空";
    /** AI 配置名称最大长度。 */
    public static final int AI_CONFIG_NAME_MAX_LENGTH = 80;
    /** AI 服务基础地址最大长度。 */
    public static final int AI_BASE_URL_MAX_LENGTH = 1024;
    /** AI 模型编码最大长度。 */
    public static final int AI_MODEL_MAX_LENGTH = 160;
    /** AI 服务 API Key最大长度。 */
    public static final int AI_API_KEY_MAX_LENGTH = 2048;
    /** AI 代理主机最大长度。 */
    public static final int AI_PROXY_HOST_MAX_LENGTH = 255;
    /** AI 代理用户名最大长度。 */
    public static final int AI_PROXY_USERNAME_MAX_LENGTH = 255;
    /** AI 代理密码最大长度。 */
    public static final int AI_PROXY_PASSWORD_MAX_LENGTH = 512;
    /** AI 配置备注最大长度。 */
    public static final int AI_REMARK_MAX_LENGTH = 500;
    /** AI 配置名称必填校验提示。 */
    public static final String AI_CONFIG_NAME_REQUIRED = "配置名称不能为空";
    /** AI 配置名称超出长度限制提示。 */
    public static final String AI_CONFIG_NAME_TOO_LONG = "配置名称不能超过 80 个字符";
    /** AI 服务商必填校验提示。 */
    public static final String AI_PROVIDER_REQUIRED = "AI 服务商不能为空";
    /** AI 服务基础地址必填校验提示。 */
    public static final String AI_BASE_URL_REQUIRED = "API Base URL 不能为空";
    /** AI 服务基础地址超出长度限制提示。 */
    public static final String AI_BASE_URL_TOO_LONG = "API Base URL 不能超过 1024 个字符";
    /** AI 服务基础地址格式或取值无效提示。 */
    public static final String AI_BASE_URL_INVALID = "API Base URL 必须是有效的 HTTP 或 HTTPS 地址";
    /** AI 模型编码超出长度限制提示。 */
    public static final String AI_MODEL_TOO_LONG = "默认模型不能超过 160 个字符";
    /** AI 服务 API Key超出长度限制提示。 */
    public static final String AI_API_KEY_TOO_LONG = "API Key 不能超过 2048 个字符";
    /** AI 代理主机超出长度限制提示。 */
    public static final String AI_PROXY_HOST_TOO_LONG = "代理主机不能超过 255 个字符";
    /** AI 代理端口格式或取值无效提示。 */
    public static final String AI_PROXY_PORT_INVALID = "代理端口必须在 1 到 65535 之间";
    /** AI 代理用户名超出长度限制提示。 */
    public static final String AI_PROXY_USERNAME_TOO_LONG = "代理用户名不能超过 255 个字符";
    /** AI 代理密码超出长度限制提示。 */
    public static final String AI_PROXY_PASSWORD_TOO_LONG = "代理密码不能超过 512 个字符";
    /** AI 配置备注超出长度限制提示。 */
    public static final String AI_REMARK_TOO_LONG = "备注不能超过 500 个字符";

    /** 禁止实例化常量类。 */
    private ValidationConstants() {
    }
}
