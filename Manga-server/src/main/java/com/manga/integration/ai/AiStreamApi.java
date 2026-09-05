package com.manga.integration.ai;

import com.manga.common.constant.ExternalHttpEndpointConstants;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

import reactor.core.publisher.Flux;

import java.util.Map;

/**
 * AI 流式输出的声明式 HTTP 契约。
 */
@HttpExchange(accept = MediaType.TEXT_EVENT_STREAM_VALUE, contentType = MediaType.APPLICATION_JSON_VALUE)
public interface AiStreamApi {

    /** 以 SSE 方式流式获取 OpenAI 兼容模型输出。 */
    @PostExchange(ExternalHttpEndpointConstants.OPENAI_CHAT_COMPLETIONS_PATH)
    Flux<String> streamChatCompletions(@RequestHeader Map<String, String> headers, @RequestBody String requestBody);
}
