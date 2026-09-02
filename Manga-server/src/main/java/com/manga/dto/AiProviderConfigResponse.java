package com.manga.dto;

import com.manga.common.enums.AiProviderType;
import com.manga.common.enums.AiProxyType;

import java.time.LocalDateTime;
import java.util.List;

/** 返回不包含密钥明文和密文的 AI 服务配置摘要。 */
public record AiProviderConfigResponse(
        /** AI 服务配置主键。 */
        Long id,
        /** AI 服务配置名称。 */
        String name,
        /** AI 服务商类型。 */
        AiProviderType providerType,
        /** AI 服务基础地址。 */
        String baseUrl,
        /** 默认模型编码。 */
        String defaultModel,
        /** 是否已经保存 API Key。 */
        boolean hasApiKey,
        /** API Key 脱敏摘要。 */
        String apiKeyHint,
        /** 服务商显示名称。 */
        String providerLabel,
        /** 服务商能力说明。 */
        String providerDescription,
        /** 服务商推荐模型编码。 */
        String recommendedModel,
        /** 服务商是否要求 API Key。 */
        boolean apiKeyRequired,
        /** 服务商支持的生成能力列表。 */
        List<String> capabilities,
        /** 出站代理类型。 */
        AiProxyType proxyType,
        /** 出站代理主机。 */
        String proxyHost,
        /** 出站代理端口。 */
        Integer proxyPort,
        /** 出站代理认证用户名。 */
        String proxyUsername,
        /** 是否已经保存代理密码。 */
        boolean hasProxyPassword,
        /** 代理密码脱敏摘要。 */
        String proxyPasswordHint,
        /** 配置是否启用。 */
        boolean enabled,
        /** 是否为默认配置。 */
        boolean defaultConfig,
        /** 配置备注。 */
        String remark,
        /** 创建时间。 */
        LocalDateTime createdAt,
        /** 最后更新时间。 */
        LocalDateTime updatedAt
) {
}
