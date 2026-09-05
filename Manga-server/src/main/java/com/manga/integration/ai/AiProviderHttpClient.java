package com.manga.integration.ai;

import com.manga.common.constant.AiProviderConstants;
import com.manga.common.enums.AiProviderType;
import com.manga.integration.http.ExternalHttpClientFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientResponseException;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 统一执行 AI 服务的连接检测 HTTP 请求。
 */
@Component
@RequiredArgsConstructor
public class AiProviderHttpClient {

    /** 外部 HTTP 客户端工厂。 */
    private final ExternalHttpClientFactory externalHttpClientFactory;

    /**
     * 执行 AI 服务连接检测请求。
     */
    public ResponseEntity<String> probe(AiProviderType providerType, String baseUrl, String apiKey,
            AiProviderProxySettings proxySettings) {
        AiProviderApi api = externalHttpClientFactory.createRestService(
                AiProviderApi.class,
                baseUrl,
                proxySettings == null ? null : proxySettings.toExternalProxySettings());
        try {
            return switch (providerType) {
                case ANTHROPIC -> api.anthropicModels(anthropicHeaders(apiKey));
                case GEMINI -> api.geminiModels(apiKey);
                case NEWAPI -> api.newApiUsageStats(apiKey);
                case OLLAMA -> api.ollamaTags();
                case COMFYUI -> api.comfyuiSystemStats();
                case DASHSCOPE -> api.dashscopeModels(authorizationHeaders(apiKey));
                case VOLCENGINE -> api.volcengineModels(authorizationHeaders(apiKey));
                case CUSTOM -> api.customRoot(customHeaders(apiKey));
                default -> api.openAiModels(authorizationHeaders(apiKey));
            };
        } catch (RestClientResponseException exception) {
            HttpHeaders headers = exception.getResponseHeaders();
            return ResponseEntity.status(exception.getStatusCode())
                    .headers(headers)
                    .body(exception.getResponseBodyAsString());
        }
    }

    /** 创建 OpenAI 兼容认证请求头。 */
    private Map<String, String> authorizationHeaders(String apiKey) {
        Map<String, String> headers = new LinkedHashMap<>();
        if (apiKey != null && !apiKey.isBlank()) {
            headers.put(HttpHeaders.AUTHORIZATION, AiProviderConstants.BEARER_PREFIX + apiKey);
        }
        return headers;
    }

    /** 创建 Anthropic 认证请求头。 */
    private Map<String, String> anthropicHeaders(String apiKey) {
        Map<String, String> headers = new LinkedHashMap<>();
        if (apiKey != null && !apiKey.isBlank()) {
            headers.put(AiProviderConstants.ANTHROPIC_API_KEY_HEADER, apiKey);
            headers.put(AiProviderConstants.ANTHROPIC_VERSION_HEADER, AiProviderConstants.ANTHROPIC_VERSION);
        }
        return headers;
    }

    /** 创建自定义服务请求头。 */
    private Map<String, String> customHeaders(String apiKey) {
        return authorizationHeaders(apiKey);
    }
}
