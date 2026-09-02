package com.manga.dto;

import com.manga.common.enums.AiProviderType;

import java.util.List;

/** 返回前端渲染服务商预设所需的公开元数据。 */
public record AiProviderOptionResponse(
        /** AI 服务商类型。 */
        AiProviderType providerType,
        /** 前端显示名称。 */
        String label,
        /** 服务商默认基础地址。 */
        String defaultBaseUrl,
        /** 服务商推荐模型编码。 */
        String recommendedModel,
        /** 服务商是否要求 API Key。 */
        boolean apiKeyRequired,
        /** 服务商支持的生成能力列表。 */
        List<String> capabilities,
        /** AI 服务商选项描述。 */
        String description
) {
}
