package com.manga.dto;

import com.manga.common.enums.AiProviderType;
import com.manga.common.enums.AiProxyType;

/** 向未来 AI 调用层提供已解密但不对外序列化的接入配置。 */
public record ResolvedAiProviderConfig(
        /** 已解析 AI 服务配置主键。 */
        Long id,
        /** AI 服务商类型。 */
        AiProviderType providerType,
        /** AI 服务基础地址。 */
        String baseUrl,
        /** 默认模型编码。 */
        String defaultModel,
        /** AI 服务 API Key 明文，仅在请求处理中短暂使用。 */
        String apiKey,
        /** 出站代理类型。 */
        AiProxyType proxyType,
        /** 出站代理主机。 */
        String proxyHost,
        /** 出站代理端口。 */
        Integer proxyPort,
        /** 出站代理认证用户名。 */
        String proxyUsername,
        /** 出站代理密码明文，仅在请求处理中短暂使用。 */
        String proxyPassword
) {
    /** 返回不包含密钥明文的日志字符串。 */
    @Override
    public String toString() {
        return "ResolvedAiProviderConfig[id=%s, providerType=%s, baseUrl=%s, defaultModel=%s, apiKeyPresent=%s, proxyType=%s, proxyPasswordPresent=%s]"
                .formatted(id, providerType, baseUrl, defaultModel, apiKey != null && !apiKey.isBlank(),
                        proxyType, proxyPassword != null && !proxyPassword.isBlank());
    }
}
