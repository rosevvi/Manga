package com.manga.agent;

import com.manga.common.enums.AiProviderType;
import com.manga.common.enums.AiProxyType;
import com.manga.common.exception.BusinessException;
import com.manga.dto.ResolvedAiProviderConfig;
import io.agentscope.core.model.ChatModelBase;
import io.agentscope.core.model.transport.ProxyConfig;
import io.agentscope.extensions.model.openai.OpenAIChatModel;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/** 将当前项目可支持的 OpenAI 兼容配置转换为 AgentScope 模型。 */
@Component
public class MangaAgentModelFactory {

    public ChatModelBase create(ResolvedAiProviderConfig config, String modelCode) {
        if (!isOpenAiCompatible(config.providerType())) {
            throw new BusinessException(com.manga.common.enums.CommonResponseCode.VALIDATION_ERROR,
                    "当前默认 AI 配置不是 OpenAI 兼容文本服务", HttpStatus.UNPROCESSABLE_ENTITY);
        }
        if (config.apiKey() == null || config.apiKey().isBlank()) {
            throw new BusinessException(com.manga.common.enums.CommonResponseCode.VALIDATION_ERROR,
                    "当前默认 AI 配置缺少 API Key", HttpStatus.UNPROCESSABLE_ENTITY);
        }
        if (modelCode == null || modelCode.isBlank()) {
            throw new BusinessException(com.manga.common.enums.CommonResponseCode.VALIDATION_ERROR,
                    "当前默认 AI 配置缺少默认模型", HttpStatus.UNPROCESSABLE_ENTITY);
        }
        String rootBaseUrl = rootBaseUrl(config.baseUrl());
        OpenAIChatModel.Builder builder = OpenAIChatModel.builder()
                .apiKey(config.apiKey())
                .modelName(modelCode)
                .baseUrl(rootBaseUrl)
                .endpointPath("/v1/chat/completions")
                .stream(true);
        if (config.proxyType() == AiProxyType.HTTP) {
            builder.proxy(config.proxyUsername() == null || config.proxyUsername().isBlank()
                    ? ProxyConfig.http(config.proxyHost(), config.proxyPort())
                    : ProxyConfig.http(config.proxyHost(), config.proxyPort(), config.proxyUsername(), config.proxyPassword()));
        } else if (config.proxyType() == AiProxyType.SOCKS5) {
            builder.proxy(config.proxyUsername() == null || config.proxyUsername().isBlank()
                    ? ProxyConfig.socks5(config.proxyHost(), config.proxyPort())
                    : ProxyConfig.socks5(config.proxyHost(), config.proxyPort(), config.proxyUsername(), config.proxyPassword()));
        }
        return builder.build();
    }

    public boolean isOpenAiCompatible(AiProviderType providerType) {
        return providerType == AiProviderType.OPENAI || providerType == AiProviderType.OPENAI_COMPATIBLE
                || providerType == AiProviderType.DEEPSEEK || providerType == AiProviderType.NEWAPI;
    }

    private String rootBaseUrl(String value) {
        String normalized = value == null ? "" : value.trim().replaceAll("/+$", "");
        if (normalized.endsWith("/v1")) {
            normalized = normalized.substring(0, normalized.length() - 3);
        }
        return normalized;
    }
}
