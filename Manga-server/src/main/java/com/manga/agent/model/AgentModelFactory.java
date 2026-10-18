package com.manga.agent.model;

import com.manga.agent.blueprint.ModelSnapshot;
import com.manga.agent.domain.CreativeRuntimeMessages;
import com.manga.entity.AiProviderConfig;
import com.manga.repository.AiProviderConfigRepository;
import com.manga.service.security.SecretCipher;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Objects;

/**
 * 根据 Blueprint 快照重新解析、校验并创建真实模型资源。
 */
@Component
@RequiredArgsConstructor
public class AgentModelFactory {

    private final AiProviderConfigRepository configRepository;
    private final ModelConfigurationFingerprint fingerprint;
    private final AgentModelProviderRegistry providerRegistry;
    private final SecretCipher secretCipher;

    /** 校验当前配置仍与快照一致后创建模型，禁止静默切换新配置。 */
    public OwnedChatModel create(ModelSnapshot snapshot) {
        AiProviderConfig config = configRepository.findById(snapshot.configId())
                .orElseThrow(() -> new IllegalStateException(
                        CreativeRuntimeMessages.AGENT_MODEL_CONFIG_UNAVAILABLE));
        if (!Boolean.TRUE.equals(config.getEnabled())
                || !Objects.equals(snapshot.securityFingerprint(), fingerprint.fingerprint(config))) {
            throw new IllegalStateException(
                    CreativeRuntimeMessages.AGENT_MODEL_SECURITY_FINGERPRINT_MISMATCH);
        }
        String apiKey = decrypt(config.getApiKeyCiphertext());
        String proxyPassword = decrypt(config.getProxyPasswordCiphertext());
        return providerRegistry.create(config, snapshot, apiKey, proxyPassword);
    }

    /** 仅在创建模型的最小作用域内解密凭据。 */
    private String decrypt(String ciphertext) {
        return Objects.isNull(ciphertext) ? null : secretCipher.decrypt(ciphertext);
    }
}
