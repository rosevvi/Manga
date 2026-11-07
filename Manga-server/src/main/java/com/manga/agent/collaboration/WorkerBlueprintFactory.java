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
        AgentProfile profile = profiles.require(key);
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
        AgentBlueprintFingerprintMaterial material = new AgentBlueprintFingerprintMaterial(key, profile.version(),
                parent.model(), profile.systemPrompt(), parent.stableVariables(), capabilities,
                AgentPermissionPolicy.READ_ONLY, profile.maxTurns());
        String fingerprint = codec.fingerprint(material);
        return new AgentBlueprint(new AgentKernelKey(fingerprint), key, profile.version(), parent.model(),
                profile.systemPrompt(), parent.stableVariables(), capabilities, AgentPermissionPolicy.READ_ONLY,
                profile.maxTurns(), fingerprint);
    }
}
