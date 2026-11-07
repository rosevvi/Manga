package com.manga.agent.blueprint;

import com.manga.agent.capability.CapabilityDescriptor;
import com.manga.agent.capability.CapabilityRegistry;
import com.manga.agent.domain.CreativeErrorCode;
import com.manga.agent.domain.CreativeRuntimeMessages;
import com.manga.agent.harness.AgentRuntimeException;
import com.manga.agent.profile.AgentProfileKey;
import com.manga.agent.profile.AgentProfilePolicySupport;
import com.manga.agent.capability.CapabilityName;
import com.manga.agent.domain.AgentPermissionPolicy;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;
import java.util.EnumSet;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 创建 Kernel 前复核冻结规格及当前工具实现，不重新读取可变发布指针。
 */
@Component
@RequiredArgsConstructor
public class AgentBlueprintPreflight {

    private final AgentBlueprintCodec codec;
    private final CapabilityRegistry capabilityRegistry;

    /** 验证快照自身完整性及全部可执行契约，失败时禁止创建模型。 */
    public void validate(AgentBlueprint blueprint) {
        try {
            codec.verifyFingerprint(blueprint);
            List<CapabilityManifest> currentCapabilities = capabilityRegistry
                    .descriptors(blueprint.capabilities().stream().map(CapabilityManifest::name)
                            .collect(Collectors.toUnmodifiableSet()))
                    .stream()
                    .map(this::manifest)
                    .toList();
            // 权限和精确白名单已经冻结；发布、禁用和回滚不能撤销已有规格。
            Set<CapabilityName> allowed = allowedCapabilities(blueprint);
            AgentPermissionPolicy ceiling = blueprint.profileKey() == AgentProfileKey.MANGA_DIRECTOR
                    ? AgentPermissionPolicy.PROPOSE_CHANGES : AgentPermissionPolicy.READ_ONLY;
            boolean matches = ceiling.allows(blueprint.permissionPolicy())
                    && allowed.containsAll(blueprint.capabilities().stream().map(CapabilityManifest::name).toList())
                    && blueprint.capabilities().stream().allMatch(capability -> blueprint.permissionPolicy().allows(capability.requiredPermission()))
                    && Objects.equals(currentCapabilities, blueprint.capabilities());
            if (Objects.nonNull(blueprint.executionPolicy())) {
                AgentProfilePolicySupport.requireModel(blueprint.executionPolicy().model(), blueprint.model());
                matches = matches && (!blueprint.executionPolicy().sandbox() || blueprint.permissionPolicy() == AgentPermissionPolicy.READ_ONLY);
            }
            if (!matches) {
                throw mismatch(null);
            }
        } catch (AgentRuntimeException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw mismatch(exception);
        }
    }

    /** 固定角色仅定义安全上限，精确工具清单仍以快照 manifest 为准。 */
    private Set<CapabilityName> allowedCapabilities(AgentBlueprint blueprint) {
        if (Objects.nonNull(blueprint.executionPolicy()) && blueprint.executionPolicy().sandbox()) {
            return CapabilityName.phaseTwoReadOnlyCapabilities();
        }
        if (blueprint.profileKey() == AgentProfileKey.MANGA_DIRECTOR) return CapabilityName.directorCapabilities();
        var allowed = EnumSet.copyOf(CapabilityName.phaseTwoReadOnlyCapabilities());
        allowed.add(CapabilityName.READ_DELEGATED_RESULT);
        allowed.add(CapabilityName.SUBMIT_CREATIVE_STEP_RESULT);
        return allowed;
    }

    /** 将当前能力描述转换为可比较的快照清单项。 */
    private CapabilityManifest manifest(CapabilityDescriptor descriptor) {
        return new CapabilityManifest(
                descriptor.name(),
                descriptor.schemaSha256(),
                descriptor.readOnly(),
                descriptor.concurrencySafe(),
                descriptor.requiredPermission(),
                descriptor.implementationVersion());
    }

    /** 创建不暴露内部契约细节的预检失败。 */
    private AgentRuntimeException mismatch(Throwable cause) {
        return new AgentRuntimeException(
                CreativeErrorCode.RUN_CONFIG_UNAVAILABLE,
                CreativeRuntimeMessages.AGENT_BLUEPRINT_CONTRACT_MISMATCH,
                cause);
    }
}
