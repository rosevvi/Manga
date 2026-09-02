package com.manga.common.constant;

import java.util.Set;

/** 统一维护 AI 服务配置的连接检测参数和响应文案。 */
public final class AiProviderConstants {

    /** AI 服务连接检测超时时间，单位为秒。 */
    public static final long CONNECTION_TEST_TIMEOUT_SECONDS = 8;
    /** 连接检测响应摘要最大长度。 */
    public static final int CONNECTION_TEST_RESPONSE_SUMMARY_MAX_LENGTH = 220;
    /** HTTP Authorization 请求头名称。 */
    public static final String AUTHORIZATION_HEADER = "Authorization";
    /** Bearer 认证值前缀。 */
    public static final String BEARER_PREFIX = "Bearer ";
    /** API Key 查询参数名称。 */
    public static final String API_KEY_QUERY_PARAMETER = "key";
    /** Anthropic API Key 请求头名称。 */
    public static final String ANTHROPIC_API_KEY_HEADER = "x-api-key";
    /** Anthropic API 版本请求头名称。 */
    public static final String ANTHROPIC_VERSION_HEADER = "anthropic-version";
    /** Anthropic API 默认版本。 */
    public static final String ANTHROPIC_VERSION = "2023-06-01";
    /** OpenAI 兼容接口根路径。 */
    public static final String OPENAI_API_ROOT_PATH = "/v1";
    /** Gemini 接口根路径。 */
    public static final String GEMINI_API_ROOT_PATH = "/v1beta";
    /** OpenAI 兼容模型列表路径。 */
    public static final String OPENAI_MODELS_PATH = "/models";
    /** New API 接口根路径。 */
    public static final String NEWAPI_API_ROOT_PATH = "/api/v1";
    /** New API 用量统计路径。 */
    public static final String NEWAPI_USAGE_STATS_PATH = "/usage/stats";
    /** DashScope 兼容模式接口根路径。 */
    public static final String DASHSCOPE_API_ROOT_PATH = "/compatible-mode/v1";
    /** 火山引擎方舟接口根路径。 */
    public static final String VOLCENGINE_API_ROOT_PATH = "/api/v3";
    /** Ollama 模型标签接口路径。 */
    public static final String OLLAMA_TAGS_PATH = "/api/tags";
    /** ComfyUI 系统状态接口路径。 */
    public static final String COMFYUI_SYSTEM_STATS_PATH = "/system_stats";
    /** 上游模型对象可能使用的标识字段集合。 */
    public static final Set<String> MODEL_IDENTIFIER_FIELDS = Set.of("id", "name", "model", "displayName");
    /** 上游响应可能使用的提示字段集合。 */
    public static final Set<String> RESPONSE_MESSAGE_FIELDS = Set.of(
            "message", "error", "errors", "error_description", "msg", "detail", "reason", "title");

    /** 代理主机为空时的校验提示。 */
    public static final String PROXY_HOST_REQUIRED = "启用代理时代理主机不能为空";
    /** 代理端口无效时的校验提示。 */
    public static final String PROXY_PORT_INVALID = "启用代理时代理端口必须在 1 到 65535 之间";
    /** 代理认证用户名为空时的校验提示。 */
    public static final String PROXY_USERNAME_REQUIRED = "启用代理认证时代理用户名不能为空";
    /** 连接检测 HTTP 状态提示模板。 */
    public static final String CONNECTION_TEST_HTTP_STATUS_TEMPLATE = "HTTP %d";
    /** 带正文摘要的连接检测提示模板。 */
    public static final String CONNECTION_TEST_HTTP_STATUS_WITH_BODY_TEMPLATE = "HTTP %d：%s";
    /** 目标模型可用提示模板。 */
    public static final String CONNECTION_TEST_MODEL_OK_TEMPLATE = "HTTP %d：模型可用";
    /** 模型列表无法识别时的提示。 */
    public static final String CONNECTION_TEST_MODEL_LIST_UNREADABLE = "服务已响应，但模型列表格式无法识别";
    /** 目标模型不存在提示模板。 */
    public static final String CONNECTION_TEST_MODEL_NOT_FOUND_TEMPLATE = "HTTP %d：未找到模型：%s";
    /** 连接检测缺少 API Key 时的提示。 */
    public static final String CONNECTION_TEST_API_KEY_REQUIRED = "该服务商需要先保存 API Key";
    /** AI 服务连接检测失败提示。 */
    public static final String CONNECTION_TEST_FAILED = "连接失败，请检查服务地址、网络或代理配置";
    /** AI 服务连接检测中断提示。 */
    public static final String CONNECTION_TEST_INTERRUPTED = "连接检测被中断";
    /** 敏感文本统一替换内容。 */
    public static final String MASKED_SECRET_TEXT = "[已隐藏]";

    /** 禁止实例化常量类。 */
    private AiProviderConstants() {
    }
}
