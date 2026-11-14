package com.manga.agent.blueprint;

import com.manga.agent.capability.CapabilityDescriptor;
import com.manga.agent.capability.CapabilityRegistry;
import com.manga.agent.config.CreativeAgentProperties;
import com.manga.agent.domain.AgentPermissionPolicy;
import com.manga.agent.domain.CreativeRuntimeMessages;
import com.manga.agent.model.ModelConfigurationFingerprint;
import com.manga.agent.model.AgentModelProviderRegistry;
import com.manga.agent.profile.AgentProfile;
import com.manga.agent.profile.AgentProfileSource;
import com.manga.agent.profile.AgentExecutionPolicy;
import com.manga.agent.profile.AgentBudgetLimits;
import com.manga.agent.profile.AgentModelSelection;
import com.manga.agent.profile.AgentProfileContracts;
import com.manga.agent.profile.AgentProfileResponseCode;
import com.manga.agent.profile.AgentProfilePolicySupport;
import com.manga.agent.capability.CapabilityName;
import com.manga.common.exception.BusinessException;
import com.manga.entity.AiProviderConfig;
import com.manga.common.enums.AiModelType;
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
    private final AgentModelProviderRegistry modelProviders;

    public AgentBlueprintCompiler(
            AgentProfileSource profileSource,
            CapabilityRegistry capabilityRegistry,
            AiProviderConfigRepository modelConfigRepository,
            CreativeAgentProperties properties,
            ModelConfigurationFingerprint modelFingerprint,
            AgentBlueprintCodec blueprintCodec,
            AgentModelProviderRegistry modelProviders) {
        this.profileSource = profileSource;
        this.capabilityRegistry = capabilityRegistry;
        this.modelConfigRepository = modelConfigRepository;
        this.properties = properties;
        this.modelFingerprint = modelFingerprint;
        this.blueprintCodec = blueprintCodec;
        this.modelProviders = modelProviders;
    }

    /** 编译并序列化 Blueprint；密钥只参与内存中的摘要计算，不进入快照。 */
    public CompiledAgentBlueprint compile(AgentBlueprintCompileRequest request) {
        AgentProfile profile = Objects.isNull(request.profileId()) ? profileSource.require(request.profileKey())
                : profileSource.require(request.profileId());
        return compileResolved(profile, request, false, profile.maxTurns());
    }

    /** 管理员测试精确版本；只读工具子集和预算固定进快照，不调用模型。 */
    public CompiledAgentBlueprint compileSandbox(AgentProfile profile, AgentBlueprintCompileRequest request, int maxTurns) {
        return compileResolved(profile, request, true, Math.min(profile.maxTurns(), maxTurns));
    }

    /** 将选定版本和实际模型决策冻结成完整规格。 */
    private CompiledAgentBlueprint compileResolved(AgentProfile profile, AgentBlueprintCompileRequest request, boolean sandbox, int turns) {
        requirePermission(profile, request.permissionPolicy());
        AgentExecutionPolicy policy = profile.executionPolicy();
        if (!sandbox && !AgentProfilePolicySupport.rootSelectable(profile)) throw new BusinessException(AgentProfileResponseCode.UNAVAILABLE);
        if (Objects.nonNull(policy) && policy.model().selection() == AgentModelSelection.USER_DEFAULT
                && Objects.nonNull(request.modelConfigId())) throw new BusinessException(AgentProfileResponseCode.INVALID);
        ModelSnapshot model = modelSnapshot(requireModelConfig(request));
        if (!modelProviders.supports(model.providerType())) throw new BusinessException(AgentProfileResponseCode.MODEL_UNAVAILABLE);
        if (Objects.nonNull(policy)) AgentProfilePolicySupport.requireModel(policy.model(), model);
        if (sandbox) {
            if (Objects.isNull(policy) || request.permissionPolicy() != AgentPermissionPolicy.READ_ONLY) {
                throw new BusinessException(AgentProfileResponseCode.INVALID);
            }
            policy = new AgentExecutionPolicy(policy.profileId(), policy.displayName(), policy.model(),
                    new AgentBudgetLimits(1, 1, 1, 0, 1, turns + 1), true);
        }
        List<CapabilityManifest> capabilities = capabilityRegistry.descriptors(profile.allowedCapabilities()).stream()
                .filter(capability -> request.permissionPolicy().allows(capability.requiredPermission()))
                .filter(capability -> !sandbox || CapabilityName.phaseTwoReadOnlyCapabilities().contains(capability.name()))
                .map(this::manifest).toList();
        String prompt = profile.systemPrompt() + (sandbox ? AgentProfileContracts.SANDBOX_PROMPT : "");
        var material = new AgentBlueprintFingerprintMaterial(profile.key(), profile.version(), model, prompt,
                request.stableVariables(), capabilities, request.permissionPolicy(), turns, policy);
        String fingerprint = blueprintCodec.fingerprint(material);
        var blueprint = new AgentBlueprint(new AgentKernelKey(fingerprint), profile.key(), profile.version(), model, prompt,
                request.stableVariables(), capabilities, request.permissionPolicy(), turns, fingerprint, policy);
        return new CompiledAgentBlueprint(blueprint, blueprintCodec.serialize(blueprint));
    }

    /** 解析指定或默认模型配置，并拒绝跨用户或已停用配置。 */
    private AiProviderConfig requireModelConfig(AgentBlueprintCompileRequest request) {
        AiProviderConfig config = Objects.isNull(request.modelConfigId())
                ? modelConfigRepository.findDefaultOwnedBy(request.userId()).orElse(null)
                : modelConfigRepository.findOwnedById(request.modelConfigId(), request.userId()).orElse(null);
        if (Objects.isNull(config) || !Boolean.TRUE.equals(config.getEnabled())) {
            throw new BusinessException(AgentProfileResponseCode.MODEL_UNAVAILABLE);
        }
        if ((Objects.nonNull(config.getModelType()) && config.getModelType() != AiModelType.TEXT)
                || Objects.isNull(config.getDefaultModel()) || config.getDefaultModel().isBlank()) {
            throw new BusinessException(AgentProfileResponseCode.MODEL_UNAVAILABLE);
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

    /** 工具描述符进入冻结白名单和指纹。 */
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

    /** 请求可以缩小 Profile 权限，但不能越过其最高授权。 */
    private void requirePermission(AgentProfile profile, AgentPermissionPolicy requested) {
        if (Objects.isNull(requested) || !profile.permissionPolicy().allows(requested)) {
            throw new IllegalArgumentException(CreativeRuntimeMessages.AGENT_PERMISSION_EXCEEDS_PROFILE);
        }
    }

}
