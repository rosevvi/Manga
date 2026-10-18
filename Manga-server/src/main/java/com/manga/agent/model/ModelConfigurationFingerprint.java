package com.manga.agent.model;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.manga.agent.capability.CapabilityContractSupport;
import com.manga.agent.domain.CreativeRuntimeMessages;
import com.manga.common.enums.AiProviderType;
import com.manga.common.enums.AiProxyType;
import com.manga.entity.AiProviderConfig;
import org.springframework.stereotype.Component;

/**
 * 计算包含敏感密文变化但不暴露明文或密文的模型配置指纹。
 */
@Component
public class ModelConfigurationFingerprint {

    private final ObjectMapper canonicalMapper;

    public ModelConfigurationFingerprint(ObjectMapper objectMapper) {
        canonicalMapper = objectMapper.copy().enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);
    }

    /** 返回模型配置的安全 SHA-256 指纹。 */
    public String fingerprint(AiProviderConfig config) {
        ModelSecurityFingerprintMaterial material = new ModelSecurityFingerprintMaterial(
                config.getId(),
                config.getProviderType(),
                config.getBaseUrl(),
                config.getDefaultModel(),
                config.getApiKeyCiphertext(),
                config.getProxyType(),
                config.getProxyHost(),
                config.getProxyPort(),
                config.getProxyUsername(),
                config.getProxyPasswordCiphertext()
        );
        try {
            return CapabilityContractSupport.sha256(canonicalMapper.writeValueAsString(material));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException(
                    CreativeRuntimeMessages.AGENT_BLUEPRINT_SERIALIZATION_FAILED, exception);
        }
    }

    /** 只在内存中参与摘要计算的模型安全材料。 */
    private record ModelSecurityFingerprintMaterial(
            /** 模型配置主键。 */ Long configId,
            /** 服务商类型。 */ AiProviderType providerType,
            /** 服务基础地址。 */ String baseUrl,
            /** 模型编码。 */ String modelName,
            /** API Key 密文，仅参与摘要。 */ String apiKeyCiphertext,
            /** 代理类型。 */ AiProxyType proxyType,
            /** 代理主机。 */ String proxyHost,
            /** 代理端口。 */ Integer proxyPort,
            /** 代理用户名。 */ String proxyUsername,
            /** 代理密码密文，仅参与摘要。 */ String proxyPasswordCiphertext
    ) {
    }
}
