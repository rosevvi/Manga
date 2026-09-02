package com.manga.common.enums;

import java.util.List;

/** 定义当前支持配置的 AI 接入协议与服务商类型。 */
public enum AiProviderType {
    /** OpenAI 官方服务。 */
    OPENAI("OpenAI", "https://api.openai.com", "gpt-4o-mini", true,
            List.of("TEXT", "IMAGE", "VIDEO"), "OpenAI 官方模型和兼容的 Responses / Images / Video 能力"),
    /** OpenAI 兼容协议服务。 */
    OPENAI_COMPATIBLE("OpenAI Compatible", "", "", true,
            List.of("TEXT"), "兼容 OpenAI Chat Completions 或 Responses 的第三方网关"),
    /** Anthropic Claude 服务。 */
    ANTHROPIC("Anthropic", "https://api.anthropic.com", "claude-3-5-sonnet-latest", true,
            List.of("TEXT"), "Claude 系列文本与多模态理解模型"),
    /** Google Gemini 服务。 */
    GEMINI("Gemini", "https://generativelanguage.googleapis.com", "gemini-2.0-flash", true,
            List.of("TEXT", "IMAGE"), "Google AI Studio / Gemini Developer API"),
    /** DeepSeek 官方服务。 */
    DEEPSEEK("DeepSeek", "https://api.deepseek.com", "deepseek-chat", true,
            List.of("TEXT"), "DeepSeek 官方 OpenAI 兼容文本模型"),
    /** 阿里云百炼 DashScope 服务。 */
    DASHSCOPE("DashScope", "https://dashscope.aliyuncs.com", "qwen-plus", true,
            List.of("TEXT", "IMAGE", "VIDEO"), "阿里云百炼 / DashScope，覆盖通义千问、万相等模型"),
    /** 本地 Ollama 模型服务。 */
    OLLAMA("Ollama", "http://localhost:11434", "llama3.1", false,
            List.of("TEXT"), "本地部署的开源模型服务"),
    /** 火山引擎方舟服务。 */
    VOLCENGINE("Volcengine", "https://ark.cn-beijing.volces.com", "", true,
            List.of("TEXT", "IMAGE"), "火山引擎方舟大模型服务"),
    /** New API 聚合网关。 */
    NEWAPI("New API", "", "", true,
            List.of("TEXT", "IMAGE", "VIDEO"), "OpenAI 兼容聚合网关，可承接多类生成协议"),
    /** 可灵图片与视频服务。 */
    KLING("Kling", "https://api.klingai.com", "", true,
            List.of("IMAGE", "VIDEO"), "可灵图片与视频生成服务"),
    /** 即梦图片与视频服务。 */
    JIMENG("Jimeng", "", "", true,
            List.of("IMAGE", "VIDEO"), "即梦图片与视频生成服务或兼容网关"),
    /** ComfyUI 工作流服务。 */
    COMFYUI("ComfyUI", "http://localhost:8188", "", false,
            List.of("IMAGE", "VIDEO"), "本地或远程 ComfyUI 工作流服务"),
    /** 用户自定义 AI 服务。 */
    CUSTOM("Custom", "", "", false,
            List.of("TEXT", "IMAGE", "VIDEO"), "自定义 AI 服务接入配置");

    /** 前端显示名称。 */
    private final String label;
    /** 服务商默认基础地址。 */
    private final String defaultBaseUrl;
    /** 服务商推荐模型编码。 */
    private final String recommendedModel;
    /** 服务商是否要求 API Key。 */
    private final boolean apiKeyRequired;
    /** 服务商支持的生成能力列表。 */
    private final List<String> capabilities;
    /** AI 服务商描述。 */
    private final String description;

    /** 初始化枚举项元数据。 */
    AiProviderType(
            String label,
            String defaultBaseUrl,
            String recommendedModel,
            boolean apiKeyRequired,
            List<String> capabilities,
            String description) {
        this.label = label;
        this.defaultBaseUrl = defaultBaseUrl;
        this.recommendedModel = recommendedModel;
        this.apiKeyRequired = apiKeyRequired;
        this.capabilities = capabilities;
        this.description = description;
    }

    /** 返回服务商显示名称。 */
    public String label() {
        return label;
    }

    /** 返回服务商默认接口地址。 */
    public String defaultBaseUrl() {
        return defaultBaseUrl;
    }

    /** 返回服务商推荐模型。 */
    public String recommendedModel() {
        return recommendedModel;
    }

    /** 返回服务商是否要求 API Key。 */
    public boolean apiKeyRequired() {
        return apiKeyRequired;
    }

    /** 返回服务商支持的能力列表。 */
    public List<String> capabilities() {
        return capabilities;
    }

    /** 返回服务商说明。 */
    public String description() {
        return description;
    }
}
