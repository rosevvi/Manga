package com.manga.dto;

import com.manga.common.enums.AiProviderType;

/** 向未来 AI 调用层提供已解密但不对外序列化的接入配置。 */
public record ResolvedAiProviderConfig(
        Long id,
        AiProviderType providerType,
        String baseUrl,
        String defaultModel,
        String apiKey
) {
    @Override
    public String toString() {
        return "ResolvedAiProviderConfig[id=%s, providerType=%s, baseUrl=%s, defaultModel=%s, apiKeyPresent=%s]"
                .formatted(id, providerType, baseUrl, defaultModel, apiKey != null && !apiKey.isBlank());
    }
}
