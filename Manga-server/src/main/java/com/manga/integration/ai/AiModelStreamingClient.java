package com.manga.integration.ai;

import com.manga.integration.http.ExternalHttpClientFactory;
import com.manga.integration.http.ExternalProxySettings;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import reactor.core.publisher.Flux;

import java.util.Map;

/**
 * 提供面向大模型流式返回的统一客户端入口。
 */
@Component
@RequiredArgsConstructor
public class AiModelStreamingClient {

    /** 外部 HTTP 客户端工厂。 */
    private final ExternalHttpClientFactory externalHttpClientFactory;

    /** 创建流式输出流。 */
    public Flux<String> streamChatCompletions(
            String baseUrl,
            Map<String, String> headers,
            String requestBody,
            ExternalProxySettings proxySettings) {
        AiStreamApi api = externalHttpClientFactory.createWebService(AiStreamApi.class, baseUrl, proxySettings);
        return api.streamChatCompletions(headers, requestBody);
    }
}
