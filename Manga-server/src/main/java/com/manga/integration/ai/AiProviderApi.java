package com.manga.integration.ai;

import com.manga.common.constant.ExternalHttpEndpointConstants;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;

import java.util.Map;

/**
 * AI 服务连接检测的声明式 HTTP 契约。
 */
@HttpExchange(accept = MediaType.APPLICATION_JSON_VALUE)
public interface AiProviderApi {

    /** 调用 OpenAI 兼容模型列表接口。 */
    @GetExchange(ExternalHttpEndpointConstants.OPENAI_MODELS_PATH)
    ResponseEntity<String> openAiModels(@RequestHeader Map<String, String> headers);

    /** 调用 Anthropic 模型列表接口。 */
    @GetExchange(ExternalHttpEndpointConstants.OPENAI_MODELS_PATH)
    ResponseEntity<String> anthropicModels(@RequestHeader Map<String, String> headers);

    /** 调用 DashScope 兼容模型列表接口。 */
    @GetExchange(ExternalHttpEndpointConstants.DASHSCOPE_MODELS_PATH)
    ResponseEntity<String> dashscopeModels(@RequestHeader Map<String, String> headers);

    /** 调用火山引擎方舟模型列表接口。 */
    @GetExchange(ExternalHttpEndpointConstants.VOLCENGINE_MODELS_PATH)
    ResponseEntity<String> volcengineModels(@RequestHeader Map<String, String> headers);

    /** 调用 Gemini 模型列表接口。 */
    @GetExchange(ExternalHttpEndpointConstants.GEMINI_MODELS_PATH)
    ResponseEntity<String> geminiModels(@RequestParam(value = "key", required = false) String apiKey);

    /** 调用 New API 用量统计接口。 */
    @GetExchange(ExternalHttpEndpointConstants.NEWAPI_USAGE_STATS_PATH)
    ResponseEntity<String> newApiUsageStats(@RequestParam(value = "key", required = false) String apiKey);

    /** 调用 Ollama 标签列表接口。 */
    @GetExchange(ExternalHttpEndpointConstants.OLLAMA_TAGS_PATH)
    ResponseEntity<String> ollamaTags();

    /** 调用 ComfyUI 系统状态接口。 */
    @GetExchange(ExternalHttpEndpointConstants.COMFYUI_SYSTEM_STATS_PATH)
    ResponseEntity<String> comfyuiSystemStats();

    /** 调用自定义服务根地址。 */
    @GetExchange(ExternalHttpEndpointConstants.EMPTY_PATH)
    ResponseEntity<String> customRoot(@RequestHeader Map<String, String> headers);
}
