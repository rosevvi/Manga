package com.manga.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.manga.common.enums.AiProviderType;
import com.manga.common.exception.BusinessException;
import com.manga.dto.ProjectScriptGenerateRequest;
import com.manga.dto.ProjectScriptResponse;
import com.manga.dto.ProjectScriptSaveRequest;
import com.manga.dto.ResolvedAiProviderConfig;
import com.manga.entity.MangaProject;
import com.manga.integration.ai.AiModelStreamingClient;
import com.manga.integration.http.ExternalProxySettings;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 通过已配置的 OpenAI 兼容模型流式生成结构化剧本。 */
@Service
@RequiredArgsConstructor
public class ProjectScriptGenerationService {

    private final ProjectService projectService;
    private final ProjectScriptService scriptService;
    private final AiProviderConfigService aiProviderConfigService;
    private final AiModelStreamingClient streamingClient;
    private final ScriptPromptLoader promptLoader;
    private final ObjectMapper objectMapper;

    /** 流式生成剧本并在完成后保存结构化结果。 */
    public Flux<ServerSentEvent<String>> generate(long projectId, ProjectScriptGenerateRequest request) {
        long ownerUserId = com.manga.common.security.SecurityUtils.requireCurrentUserId();
        String actor = com.manga.common.security.SecurityUtils.requireCurrentUsername();
        MangaProject project = projectService.requireOwnedProject(
                projectId, ownerUserId);
        ResolvedAiProviderConfig provider = aiProviderConfigService.resolveDefault(
                ownerUserId)
                .orElseThrow(() -> new BusinessException(
                        com.manga.common.enums.AiProviderConfigResponseCode.CONFIG_NOT_FOUND,
                        "请先配置并启用默认 AI 服务", org.springframework.http.HttpStatus.BAD_REQUEST));
        validateProvider(provider);
        if (provider.defaultModel() == null || provider.defaultModel().isBlank()) {
            throw new BusinessException(
                    com.manga.common.enums.AiProviderConfigResponseCode.CONFIG_NOT_FOUND,
                    "默认 AI 配置缺少模型", org.springframework.http.HttpStatus.BAD_REQUEST);
        }
        ScriptPromptTemplate prompt = promptLoader.load("script-generate.json");
        String userPrompt = prompt.userPromptTemplate()
                .replace("{{projectName}}", project.getName())
                .replace("{{genre}}", safe(project.getGenre()))
                .replace("{{aspectRatio}}", safe(project.getAspectRatio()))
                .replace("{{artStyle}}", safe(project.getArtStyle()))
                .replace("{{request}}", request.prompt());
        String requestBody = requestBody(provider, prompt, userPrompt);
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Authorization", "Bearer " + provider.apiKey());
        headers.put("Accept", MediaType.TEXT_EVENT_STREAM_VALUE);
        headers.put("Content-Type", MediaType.APPLICATION_JSON_VALUE);
        ExternalProxySettings proxy = provider.proxyType() == null ? null : new ExternalProxySettings(
                switch (provider.proxyType()) {
                    case HTTP -> com.manga.integration.http.ExternalProxyType.HTTP;
                    case SOCKS5 -> com.manga.integration.http.ExternalProxyType.SOCKS5;
                    default -> com.manga.integration.http.ExternalProxyType.NONE;
                }, provider.proxyHost(), provider.proxyPort() == null ? 0 : provider.proxyPort(),
                provider.proxyUsername(), provider.proxyPassword());
        StringBuilder generated = new StringBuilder();
        Flux<ServerSentEvent<String>> stream = streamingClient
                .streamChatCompletions(provider.baseUrl(), headers, requestBody, proxy)
                .publishOn(Schedulers.boundedElastic())
                .map(this::extractDelta)
                .filter(delta -> !delta.isEmpty())
                .doOnNext(generated::append)
                .map(delta -> ServerSentEvent.builder(delta).event("delta").build())
                .concatWith(Mono.defer(() -> saveGenerated(projectId, generated.toString(), ownerUserId, actor)))
                .onErrorResume(exception -> Flux.just(errorEvent(exception)));
        return stream;
    }

    private Mono<ServerSentEvent<String>> saveGenerated(long projectId, String generated,
            long ownerUserId, String actor) {
        try {
            String json = stripCodeFence(generated);
            JsonNode root = objectMapper.readTree(json);
            List<ProjectScriptSaveRequest.Episode> episodes = objectMapper.convertValue(
                    root.path("episodes"), objectMapper.getTypeFactory().constructCollectionType(
                            List.class, ProjectScriptSaveRequest.Episode.class));
            ProjectScriptSaveRequest parsed = new ProjectScriptSaveRequest(
                    root.path("title").asText("未命名剧本"),
                    root.path("synopsis").asText(""),
                    json,
                    "AI",
                    episodes);
            ProjectScriptResponse saved = scriptService.saveForOwner(projectId, parsed, ownerUserId, actor);
            return Mono.just(ServerSentEvent.builder(objectMapper.writeValueAsString(saved))
                    .event("complete").build());
        } catch (Exception exception) {
            return Mono.just(errorEvent(exception));
        }
    }

    private ServerSentEvent<String> errorEvent(Throwable exception) {
        return ServerSentEvent.builder(exception.getMessage() == null ? "AI 剧本生成失败" : exception.getMessage())
                .event("error").build();
    }

    private String requestBody(ResolvedAiProviderConfig provider, ScriptPromptTemplate prompt, String userPrompt) {
        try {
            return objectMapper.writeValueAsString(Map.of(
                    "model", provider.defaultModel(),
                    "stream", true,
                    "max_tokens", prompt.maxTokens(),
                    "messages", new Object[]{
                            Map.of("role", "system", "content", prompt.systemPrompt() + "\n\n输出结构：" + prompt.responseSchema()),
                            Map.of("role", "user", "content", userPrompt)
                    }));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to serialize script generation request", exception);
        }
    }

    private String extractDelta(String chunk) {
        if (chunk == null || chunk.isBlank() || chunk.contains("[DONE]")) {
            return "";
        }
        String data = chunk;
        int dataIndex = data.indexOf("data:");
        if (dataIndex >= 0) {
            data = data.substring(dataIndex + 5).trim();
        }
        try {
            JsonNode root = objectMapper.readTree(data);
            JsonNode content = root.path("choices").path(0).path("delta").path("content");
            return content.isTextual() ? content.asText() : "";
        } catch (JsonProcessingException ignored) {
            return data.startsWith("{") ? "" : data;
        }
    }

    private String stripCodeFence(String value) {
        String normalized = value.trim();
        if (normalized.startsWith("```") && normalized.endsWith("```")) {
            int firstLineEnd = normalized.indexOf('\n');
            return normalized.substring(firstLineEnd + 1, normalized.length() - 3).trim();
        }
        return normalized;
    }

    private void validateProvider(ResolvedAiProviderConfig provider) {
        if (provider.apiKey() == null || provider.apiKey().isBlank()) {
            throw new BusinessException(
                    com.manga.common.enums.AiProviderConfigResponseCode.CONFIG_NOT_FOUND,
                    "默认 AI 配置缺少 API Key", org.springframework.http.HttpStatus.BAD_REQUEST);
        }
        if (!(provider.providerType() == AiProviderType.OPENAI
                || provider.providerType() == AiProviderType.OPENAI_COMPATIBLE
                || provider.providerType() == AiProviderType.DEEPSEEK
                || provider.providerType() == AiProviderType.NEWAPI)) {
            throw new BusinessException(
                    com.manga.common.enums.AiProviderConfigResponseCode.CONFIG_NOT_FOUND,
                    "当前阶段仅支持 OpenAI 兼容流式模型", org.springframework.http.HttpStatus.BAD_REQUEST);
        }
    }

    private String safe(String value) {
        return value == null ? "未设置" : value;
    }
}
