package com.manga.agent.generation;

import com.manga.common.enums.AiProviderType;
import com.manga.common.enums.AiProxyType;
import com.manga.dto.ResolvedAiProviderConfig;

/** 只冻结计费端点和路由；凭据在执行时读取，允许正常轮换密钥。 */
public record GenerationConfigSnapshot(
        /** 配置主键。 */ Long configId,
        /** 接入类型。 */ AiProviderType providerType,
        /** 受信任的用户配置端点。 */ String baseUrl,
        /** 代理类型。 */ AiProxyType proxyType,
        /** 代理主机。 */ String proxyHost,
        /** 代理端口。 */ Integer proxyPort
) {
    /** 创建不会包含密钥、代理密码的审批快照。 */
    public static GenerationConfigSnapshot from(ResolvedAiProviderConfig config) {
        return new GenerationConfigSnapshot(config.id(), config.providerType(), config.baseUrl(), config.proxyType(), config.proxyHost(), config.proxyPort());
    }
}
