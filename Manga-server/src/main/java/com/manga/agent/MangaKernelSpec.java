package com.manga.agent;

import com.manga.common.enums.AiProviderType;

import java.util.List;

/** 一次运行固化的模型、提示词和工具白名单。 */
public record MangaKernelSpec(
        /** Agent 定义键。 */
        String agentKey,
        /** 面向用户展示的 Agent 名称。 */
        String displayName,
        /** Agent 职责描述。 */
        String description,
        /** AI 供应商配置主键。 */
        long providerConfigId,
        /** AI 供应商类型。 */
        AiProviderType providerType,
        /** AI 供应商基础地址。 */
        String baseUrl,
        /** 文本模型编码。 */
        String modelCode,
        /** 本次运行固定使用的系统提示词。 */
        String systemPrompt,
        /** 本次运行允许注册的工具名称。 */
        List<String> toolWhitelist,
        /** Harness 最大推理和工具循环次数。 */
        int maxIterations,
        /** 规范化配置内容的 SHA-256 指纹。 */
        String fingerprint
) {
}
