package com.manga.agent.blueprint;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.manga.agent.capability.CapabilityContractSupport;
import com.manga.agent.capability.CapabilityDescriptor;
import com.manga.agent.capability.CapabilityName;
import com.manga.agent.capability.CapabilityRegistry;
import com.manga.agent.capability.MangaAgentCapability;
import com.manga.agent.config.CreativeAgentProperties;
import com.manga.agent.domain.AgentPermissionPolicy;
import com.manga.agent.profile.AgentProfile;
import com.manga.agent.profile.AgentProfileKey;
import com.manga.agent.profile.AgentProfileSource;
import com.manga.agent.model.ModelConfigurationFingerprint;
import com.manga.common.enums.AiProviderType;
import com.manga.common.enums.AiProxyType;
import com.manga.entity.AiProviderConfig;
import com.manga.repository.AiProviderConfigRepository;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 验证 Blueprint 指纹稳定性、敏感信息隔离和只读权限约束。
 */
class AgentBlueprintCompilerTests {

    /** 用于验证模型配置归属和请求数据不进入 Blueprint 的测试用户。 */
    private static final long USER_ID = 18L;
    /** 用于断言敏感密文不会进入 Blueprint 快照的测试标记。 */
    private static final String API_KEY_CIPHERTEXT = "encrypted-api-key-material";
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void shouldProduceStableSnapshotWithoutSensitiveOrRequestScopedValues() throws Exception {
        AgentBlueprintCompiler compiler = compiler(modelConfig("model-a", API_KEY_CIPHERTEXT));
        AgentBlueprintCompileRequest request = new AgentBlueprintCompileRequest(
                USER_ID,
                AgentProfileKey.MANGA_DIRECTOR,
                AgentPermissionPolicy.READ_ONLY,
                null,
                Map.of("language", "zh-CN")
        );

        CompiledAgentBlueprint first = compiler.compile(request);
        CompiledAgentBlueprint second = compiler.compile(request);

        assertThat(first.blueprint().fingerprint()).isEqualTo(second.blueprint().fingerprint());
        assertThat(first.blueprint().kernelKey()).isEqualTo(second.blueprint().kernelKey());
        assertThat(first.snapshotJson())
                .doesNotContain(API_KEY_CIPHERTEXT)
                .contains("manga_director", "get_project_overview", "model-a");
        assertThat(objectMapper.readTree(first.snapshotJson()).has("userId")).isFalse();
        assertThat(objectMapper.readTree(first.snapshotJson()).has("projectId")).isFalse();
        assertThat(objectMapper.readTree(first.snapshotJson()).has("runId")).isFalse();
        assertThat(objectMapper.readTree(first.snapshotJson()).has("stepId")).isFalse();
        assertThat(new AgentBlueprintCodec(objectMapper).deserialize(first.snapshotJson()))
                .isEqualTo(first.blueprint());
    }

    @Test
    void shouldChangeFingerprintWhenModelContractChanges() {
        AgentBlueprintCompileRequest request = new AgentBlueprintCompileRequest(
                USER_ID,
                AgentProfileKey.MANGA_DIRECTOR,
                AgentPermissionPolicy.READ_ONLY,
                null,
                Map.of()
        );

        String first = compiler(modelConfig("model-a", API_KEY_CIPHERTEXT))
                .compile(request).blueprint().fingerprint();
        String second = compiler(modelConfig("model-b", API_KEY_CIPHERTEXT))
                .compile(request).blueprint().fingerprint();

        assertThat(first).isNotEqualTo(second);
    }

    @Test
    void shouldRejectPermissionAboveReadOnlyProfile() {
        AgentBlueprintCompiler compiler = compiler(modelConfig("model-a", API_KEY_CIPHERTEXT));
        AgentBlueprintCompileRequest request = new AgentBlueprintCompileRequest(
                USER_ID,
                AgentProfileKey.MANGA_DIRECTOR,
                AgentPermissionPolicy.PROPOSE_CHANGES,
                null,
                Map.of()
        );

        assertThatThrownBy(() -> compiler.compile(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("权限");
    }

    @Test
    void shouldRejectTamperedPersistedSnapshot() {
        CompiledAgentBlueprint compiled = compiler(modelConfig("model-a", API_KEY_CIPHERTEXT))
                .compile(new AgentBlueprintCompileRequest(
                        USER_ID,
                        AgentProfileKey.MANGA_DIRECTOR,
                        AgentPermissionPolicy.READ_ONLY,
                        null,
                        Map.of()));
        String tampered = compiled.snapshotJson().replace("model-a", "model-b");

        assertThatThrownBy(() -> new AgentBlueprintCodec(objectMapper).deserialize(tampered))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("契约");
    }

    private AgentBlueprintCompiler compiler(AiProviderConfig config) {
        AgentProfile profile = new AgentProfile(
                AgentProfileKey.MANGA_DIRECTOR,
                1,
                "Director",
                "Read-only director",
                "Use only the exact read-only tool whitelist.",
                CapabilityName.phaseTwoReadOnlyCapabilities(),
                AgentPermissionPolicy.READ_ONLY,
                12
        );
        AgentProfileSource profileSource = key -> profile;
        CapabilityRegistry registry = new CapabilityRegistry(capabilities());
        AiProviderConfigRepository repository = mock(AiProviderConfigRepository.class);
        when(repository.findDefaultOwnedBy(USER_ID)).thenReturn(Optional.of(config));
        CreativeAgentProperties properties = new CreativeAgentProperties(
                "classpath:unused", 1, 12,
                "classpath:unused", 1, 12,
                32768);
        return new AgentBlueprintCompiler(
                profileSource,
                registry,
                repository,
                properties,
                new ModelConfigurationFingerprint(objectMapper),
                new AgentBlueprintCodec(objectMapper)
        );
    }

    private List<MangaAgentCapability> capabilities() {
        List<MangaAgentCapability> capabilities = new ArrayList<>();
        for (CapabilityName name : CapabilityName.values()) {
            CapabilityDescriptor descriptor = CapabilityContractSupport.readOnlyDescriptor(
                    objectMapper,
                    name,
                    name.code(),
                    "{\"type\":\"object\",\"properties\":{},\"additionalProperties\":false}",
                    1
            );
            capabilities.add(mockCapability(descriptor));
        }
        return capabilities;
    }

    private MangaAgentCapability mockCapability(CapabilityDescriptor descriptor) {
        MangaAgentCapability capability = mock(MangaAgentCapability.class);
        when(capability.descriptor()).thenReturn(descriptor);
        return capability;
    }

    private AiProviderConfig modelConfig(String modelName, String apiKeyCiphertext) {
        return AiProviderConfig.builder()
                .id(7L)
                .ownerUserId(USER_ID)
                .name("default-text-model")
                .providerType(AiProviderType.OPENAI_COMPATIBLE)
                .baseUrl("https://models.example.test/v1")
                .defaultModel(modelName)
                .apiKeyCiphertext(apiKeyCiphertext)
                .apiKeyHint("sk-***-test")
                .proxyType(AiProxyType.NONE)
                .enabled(true)
                .defaultConfig(true)
                .build();
    }
}
