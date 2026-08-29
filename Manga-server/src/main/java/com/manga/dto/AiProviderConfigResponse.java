package com.manga.dto;

import com.manga.common.enums.AiProviderType;

import java.time.LocalDateTime;

/** 返回不包含密钥明文和密文的 AI 服务配置摘要。 */
public record AiProviderConfigResponse(
        Long id,
        String name,
        AiProviderType providerType,
        String baseUrl,
        String defaultModel,
        boolean hasApiKey,
        String apiKeyHint,
        boolean enabled,
        boolean defaultConfig,
        String remark,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
