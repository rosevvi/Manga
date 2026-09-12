package com.manga.agent;

import com.manga.common.enums.AiProviderType;

import java.util.List;

/** 一次运行固化的模型、提示词和工具白名单。 */
public record MangaKernelSpec(
        String agentKey,
        long providerConfigId,
        AiProviderType providerType,
        String baseUrl,
        String modelCode,
        String systemPrompt,
        List<String> toolWhitelist,
        int maxIterations,
        String fingerprint
) {
}
