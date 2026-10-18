package com.manga.agent.blueprint;

import com.manga.agent.capability.CapabilityDescriptor;
import com.manga.agent.capability.CapabilityRegistry;
import com.manga.agent.config.CreativeAgentProperties;
import com.manga.agent.domain.AgentPermissionPolicy;
import com.manga.agent.domain.CreativeRuntimeMessages;
import com.manga.agent.profile.AgentProfile;
import com.manga.agent.profile.AgentProfileSource;
import com.manga.agent.model.ModelConfigurationFingerprint;
import com.manga.entity.AiProviderConfig;
import com.manga.repository.AiProviderConfigRepository;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;

/**
 * 将 Profile、模型路由和能力白名单编译为无密钥的不可变 Blueprint。
 */
@Component
public class AgentBlueprintCompiler {

    private final AgentProfileSource profileSource;
    private final CapabilityRegistry capabilityRegistry;
    private final AiProviderConfigRepository modelConfigRepository;
    private final CreativeAgentProperties properties;
    private final ModelConfigurationFingerprint modelFingerprint;
    private final AgentBlueprintCodec blueprintCodec;

    public AgentBlueprintCompiler(
            AgentProfileSource profileSource,
            CapabilityRegistry capabilityRegistry,
            AiProviderConfigRepository modelConfigRepository,
            CreativeAgentProperties properties,
            ModelConfigurationFingerprint modelFingerprint,
            AgentBlueprintCodec blueprintCodec) {
        this.profileSource = profileSource;
        this.capabilityRegistry = capabilityRegistry;
        this.modelConfigRepository = modelConfigRepository;
        this.properties = properties;
        this.modelFingerprint = modelFingerprint;
        this.blueprintCodec = blueprintCodec;
    }

    /** 编译并序列化 Blueprint；密钥只参与内存中的摘要计算，不进入快照。 */
    public CompiledAgentBlueprint compile(AgentBlueprintCompileRequest request) {
        AgentProfile profile = profileSource.require(request.profileKey());
        requirePermission(profile, request.permissionPolicy());
        AiProviderConfig config = requireModelConfig(request);
        ModelSnapshot model = modelSnapshot(config);
        List<CapabilityManifest> capabilities = capabilityRegistry
                .descriptors(profile.allowedCapabilities())
                .stream()
                .map(this::manifest)
                .toList();
        AgentBlueprintFingerprintMaterial fingerprintMaterial = new AgentBlueprintFingerprintMaterial(
                profile.key(),
                profile.version(),
                model,
                profile.systemPrompt(),
                request.stableVariables(),
                capabilities,
                request.permissionPolicy(),
                profile.maxTurns()
        );
        String fingerprint = blueprintCodec.fingerprint(fingerprintMaterial);
        AgentBlueprint blueprint = new AgentBlueprint(
                new AgentKernelKey(fingerprint),
                profile.key(),
                profile.version(),
                model,
                profile.systemPrompt(),
                request.stableVariables(),
                capabilities,
                request.permissionPolicy(),
                profile.maxTurns(),
                fingerprint
        );
        return new CompiledAgentBlueprint(blueprint, blueprintCodec.serialize(blueprint));
    }

    /** 解析指定或默认模型配置，并拒绝跨用户或已停用配置。 */
    private AiProviderConfig requireModelConfig(AgentBlueprintCompileRequest request) {
        AiProviderConfig config = Objects.isNull(request.modelConfigId())
                ? modelConfigRepository.findDefaultOwnedBy(request.userId()).orElse(null)
                : modelConfigRepository.findOwnedById(request.modelConfigId(), request.userId()).orElse(null);
        if (Objects.isNull(config) || !Boolean.TRUE.equals(config.getEnabled())) {
            throw new IllegalStateException(CreativeRuntimeMessages.AGENT_MODEL_CONFIG_UNAVAILABLE);
        }
        if (Objects.isNull(config.getDefaultModel()) || config.getDefaultModel().isBlank()) {
            throw new IllegalStateException(CreativeRuntimeMessages.AGENT_MODEL_NAME_REQUIRED);
        }
        return config;
    }

    /** 构造不含密钥、密码和用户标识的模型快照。 */
    private ModelSnapshot modelSnapshot(AiProviderConfig config) {
        return new ModelSnapshot(
                config.getId(),
                config.getProviderType(),
                config.getBaseUrl(),
                config.getDefaultModel(),
                modelFingerprint.fingerprint(config),
                properties.contextWindow()
        );
    }

    private CapabilityManifest manifest(CapabilityDescriptor descriptor) {
        return new CapabilityManifest(
                descriptor.name(),
                descriptor.schemaSha256(),
                descriptor.readOnly(),
                descriptor.concurrencySafe(),
                descriptor.requiredPermission(),
                descriptor.implementationVersion()
        );
    }

    /** 阶段二只允许与只读 Director Profile 完全一致的权限。 */
    private void requirePermission(AgentProfile profile, AgentPermissionPolicy requested) {
        if (!Objects.equals(profile.permissionPolicy(), requested)) {
            throw new IllegalArgumentException(CreativeRuntimeMessages.AGENT_PERMISSION_EXCEEDS_PROFILE);
        }
    }

}
