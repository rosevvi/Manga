package com.manga.agent.model;

import com.manga.agent.blueprint.ModelSnapshot;
import com.manga.agent.domain.CreativeRuntimeMessages;
import com.manga.common.enums.AiProviderType;
import com.manga.entity.AiProviderConfig;
import com.manga.repository.AiProviderConfigRepository;
import com.manga.service.security.SecretCipher;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 验证模型创建前的快照校验和凭据最小化解密边界。
 */
class AgentModelFactoryTests {

    private static final long CONFIG_ID = 7L;
    private static final String API_KEY_CIPHERTEXT = "encrypted-api-key";
    private static final String SECURITY_FINGERPRINT = "security-fingerprint";

    @Test
    void shouldRejectChangedConfigurationBeforeDecryptingSecrets() {
        AiProviderConfigRepository repository = mock(AiProviderConfigRepository.class);
        ModelConfigurationFingerprint fingerprint = mock(ModelConfigurationFingerprint.class);
        AgentModelProviderRegistry registry = mock(AgentModelProviderRegistry.class);
        SecretCipher secretCipher = mock(SecretCipher.class);
        AiProviderConfig config = config();
        when(repository.findById(CONFIG_ID)).thenReturn(Optional.of(config));
        when(fingerprint.fingerprint(config)).thenReturn("changed-fingerprint");
        AgentModelFactory factory = new AgentModelFactory(
                repository, fingerprint, registry, secretCipher);

        assertThatThrownBy(() -> factory.create(snapshot()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage(CreativeRuntimeMessages.AGENT_MODEL_SECURITY_FINGERPRINT_MISMATCH);
        verify(secretCipher, never()).decrypt(API_KEY_CIPHERTEXT);
        verifyNoInteractions(registry);
    }

    @Test
    void shouldDecryptOnlyWhenSnapshotStillMatches() {
        AiProviderConfigRepository repository = mock(AiProviderConfigRepository.class);
        ModelConfigurationFingerprint fingerprint = mock(ModelConfigurationFingerprint.class);
        AgentModelProviderRegistry registry = mock(AgentModelProviderRegistry.class);
        SecretCipher secretCipher = mock(SecretCipher.class);
        OwnedChatModel ownedModel = mock(OwnedChatModel.class);
        AiProviderConfig config = config();
        ModelSnapshot snapshot = snapshot();
        when(repository.findById(CONFIG_ID)).thenReturn(Optional.of(config));
        when(fingerprint.fingerprint(config)).thenReturn(SECURITY_FINGERPRINT);
        when(secretCipher.decrypt(API_KEY_CIPHERTEXT)).thenReturn("plaintext-api-key");
        when(registry.create(config, snapshot, "plaintext-api-key", null)).thenReturn(ownedModel);
        AgentModelFactory factory = new AgentModelFactory(
                repository, fingerprint, registry, secretCipher);

        assertThat(factory.create(snapshot)).isSameAs(ownedModel);
        verify(secretCipher).decrypt(API_KEY_CIPHERTEXT);
        verify(registry).create(config, snapshot, "plaintext-api-key", null);
    }

    private AiProviderConfig config() {
        return AiProviderConfig.builder()
                .id(CONFIG_ID)
                .providerType(AiProviderType.OPENAI_COMPATIBLE)
                .baseUrl("https://models.example.test/v1")
                .defaultModel("model-a")
                .apiKeyCiphertext(API_KEY_CIPHERTEXT)
                .enabled(true)
                .build();
    }

    private ModelSnapshot snapshot() {
        return new ModelSnapshot(
                CONFIG_ID,
                AiProviderType.OPENAI_COMPATIBLE,
                "https://models.example.test/v1",
                "model-a",
                SECURITY_FINGERPRINT,
                32768
        );
    }
}
