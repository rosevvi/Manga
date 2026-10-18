package com.manga.agent.blueprint;

import com.manga.agent.capability.CapabilityDescriptor;
import com.manga.agent.capability.CapabilityRegistry;
import com.manga.agent.domain.CreativeErrorCode;
import com.manga.agent.domain.CreativeRuntimeMessages;
import com.manga.agent.harness.AgentRuntimeException;
import com.manga.agent.profile.AgentProfile;
import com.manga.agent.profile.AgentProfileSource;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;

/**
 * 在创建 Kernel 前复核持久化 Blueprint 与当前 Profile、能力实现契约。
 */
@Component
@RequiredArgsConstructor
public class AgentBlueprintPreflight {

    private final AgentBlueprintCodec codec;
    private final AgentProfileSource profileSource;
    private final CapabilityRegistry capabilityRegistry;

    /** 验证快照自身完整性及全部可执行契约，失败时禁止创建模型。 */
    public void validate(AgentBlueprint blueprint) {
        try {
            codec.verifyFingerprint(blueprint);
            AgentProfile profile = profileSource.require(blueprint.profileKey());
            List<CapabilityManifest> currentCapabilities = capabilityRegistry
                    .descriptors(profile.allowedCapabilities())
                    .stream()
                    .map(this::manifest)
                    .toList();
            boolean matches = profile.version() == blueprint.profileVersion()
                    && Objects.equals(profile.systemPrompt(), blueprint.systemPrompt())
                    && profile.maxTurns() == blueprint.maxTurns()
                    && Objects.equals(profile.permissionPolicy(), blueprint.permissionPolicy())
                    && Objects.equals(currentCapabilities, blueprint.capabilities());
            if (!matches) {
                throw mismatch(null);
            }
        } catch (AgentRuntimeException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw mismatch(exception);
        }
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
