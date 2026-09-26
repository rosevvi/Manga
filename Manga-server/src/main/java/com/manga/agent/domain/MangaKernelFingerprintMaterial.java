package com.manga.agent.domain;

import com.manga.common.enums.AiProviderType;

import java.util.List;

/**
 * 定义计算 Agent Kernel 指纹时参与规范化序列化的稳定字段。
 */
public record MangaKernelFingerprintMaterial(
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
        /** 系统提示词。 */
        String systemPrompt,
        /** 工具白名单。 */
        List<String> tools,
        /** 最大推理和工具循环次数。 */
        int maxIterations
) {

    public MangaKernelFingerprintMaterial {
        tools = List.copyOf(tools);
    }
}
