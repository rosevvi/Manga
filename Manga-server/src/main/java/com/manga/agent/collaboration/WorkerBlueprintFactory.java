package com.manga.agent.collaboration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.manga.agent.blueprint.AgentBlueprint;
import com.manga.agent.blueprint.AgentBlueprintCodec;
import com.manga.agent.blueprint.AgentBlueprintFingerprintMaterial;
import com.manga.agent.blueprint.AgentKernelKey;
import com.manga.agent.blueprint.CapabilityManifest;
import com.manga.agent.capability.CapabilityDescriptor;
import com.manga.agent.capability.CapabilityName;
import com.manga.agent.domain.AgentPermissionPolicy;
import com.manga.agent.profile.AgentProfile;
import com.manga.agent.profile.AgentProfileKey;
import com.manga.agent.profile.AgentProfileSource;
import com.manga.agent.profile.AgentProfilePolicySupport;
import com.manga.agent.profile.AgentProfileResponseCode;
import com.manga.common.exception.BusinessException;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import java.util.ArrayList;
import java.util.Comparator;

/** Worker 继承父节点冻结的模型，并将业务权限缩小到只读；不读取最新默认模型配置。 */
@Component
@RequiredArgsConstructor
public class WorkerBlueprintFactory {
    private final AgentProfileSource profiles;
    private final AgentBlueprintCodec codec;
    private final ObjectMapper mapper;

    /** 在计划提交时编译并冻结独立的 Worker 执行规格。 */
    public AgentBlueprint freeze(AgentProfileKey key, AgentBlueprint parent) {
        return freeze(key.code(), parent);
    }

    /** 动态身份也只能使用内置 Worker 角色和父快照中的业务读取能力。 */
    public AgentBlueprint freeze(String profileId, AgentBlueprint parent) {
        AgentProfile profile = profiles.require(profileId);
        if (profile.key() == AgentProfileKey.MANGA_DIRECTOR) throw new BusinessException(AgentProfileResponseCode.INVALID);
        if (Objects.nonNull(profile.executionPolicy())) AgentProfilePolicySupport.requireModel(profile.executionPolicy().model(), parent.model());
        String prompt = profile.systemPrompt() + "\n" + CollaborationMessages.WORKER_TEMPLATE.formatted(profile.displayName(),
                switch (profile.key()) {
                    case SCRIPT_PARSER -> StepResultKind.ANALYSIS;
                    case SCRIPT_WRITER -> StepResultKind.SCRIPT_DRAFT;
                    case STORYBOARD_DIRECTOR -> StepResultKind.STORYBOARD_DRAFT;
                    case CONTINUITY_REVIEWER -> StepResultKind.REVIEW;
                    default -> throw new BusinessException(AgentProfileResponseCode.INVALID);
                });
        ArrayList<CapabilityManifest> capabilities = new ArrayList<>(parent.capabilities().stream()
                .filter(value -> CapabilityName.phaseTwoReadOnlyCapabilities().contains(value.name()))
                .filter(value -> profile.allowedCapabilities().contains(value.name()))
                .toList());
        for (CapabilityName name : new CapabilityName[]{CapabilityName.READ_DELEGATED_RESULT, CapabilityName.SUBMIT_CREATIVE_STEP_RESULT}) {
            CapabilityDescriptor descriptor = CollaborationToolContract.descriptor(mapper, name);
            capabilities.add(new CapabilityManifest(name, descriptor.schemaSha256(), descriptor.readOnly(),
                    descriptor.concurrencySafe(), descriptor.requiredPermission(), descriptor.implementationVersion()));
        }
        capabilities.sort(Comparator.comparing(value -> value.name().code()));
        AgentBlueprintFingerprintMaterial material = new AgentBlueprintFingerprintMaterial(profile.key(), profile.version(),
                parent.model(), prompt, parent.stableVariables(), capabilities,
                AgentPermissionPolicy.READ_ONLY, profile.maxTurns(), profile.executionPolicy());
        String fingerprint = codec.fingerprint(material);
        return new AgentBlueprint(new AgentKernelKey(fingerprint), profile.key(), profile.version(), parent.model(),
                prompt, parent.stableVariables(), capabilities, AgentPermissionPolicy.READ_ONLY,
                profile.maxTurns(), fingerprint, profile.executionPolicy());
    }
}
