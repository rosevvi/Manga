package com.manga.agent.blueprint;

import com.manga.common.enums.AiProviderType;

/**
 * 固化不含密钥的模型路由信息和安全指纹。
 */
public record ModelSnapshot(
        /** 用户 AI 配置主键。 */ long configId,
        /** AI 服务协议或供应商类型。 */ AiProviderType providerType,
        /** 模型服务基础地址。 */ String baseUrl,
        /** 模型编码。 */ String modelName,
        /** 包含密钥密文摘要但不暴露密钥的配置指纹。 */ String securityFingerprint,
        /** Blueprint 编译时采用的上下文窗口大小。 */ int contextWindow
) {
}
