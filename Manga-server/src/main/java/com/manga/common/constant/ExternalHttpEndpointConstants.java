package com.manga.common.constant;

/**
 * 统一维护外部 HTTP 接口路径常量。
 */
public final class ExternalHttpEndpointConstants {

    /** 空路径。 */
    public static final String EMPTY_PATH = "";
    /** 微信 access_token 接口路径。 */
    public static final String WECHAT_ACCESS_TOKEN_PATH = "/cgi-bin/token";
    /** 微信临时二维码创建接口路径。 */
    public static final String WECHAT_QR_CODE_CREATE_PATH = "/cgi-bin/qrcode/create";
    /** OpenAI 兼容模型列表路径。 */
    public static final String OPENAI_MODELS_PATH = "/v1/models";
    /** OpenAI 兼容聊天补全路径。 */
    public static final String OPENAI_CHAT_COMPLETIONS_PATH = "/v1/chat/completions";
    /** OpenAI Responses 路径。 */
    public static final String OPENAI_RESPONSES_PATH = "/v1/responses";
    /** Gemini 模型列表路径。 */
    public static final String GEMINI_MODELS_PATH = "/v1beta/models";
    /** DashScope 兼容模型列表路径。 */
    public static final String DASHSCOPE_MODELS_PATH = "/compatible-mode/v1/models";
    /** 火山引擎方舟模型列表路径。 */
    public static final String VOLCENGINE_MODELS_PATH = "/api/v3/models";
    /** Ollama 模型标签路径。 */
    public static final String OLLAMA_TAGS_PATH = "/api/tags";
    /** ComfyUI 系统状态路径。 */
    public static final String COMFYUI_SYSTEM_STATS_PATH = "/system_stats";
    /** New API 用量统计路径。 */
    public static final String NEWAPI_USAGE_STATS_PATH = "/api/v1/usage/stats";

    /** 禁止实例化常量类。 */
    private ExternalHttpEndpointConstants() {
    }
}
