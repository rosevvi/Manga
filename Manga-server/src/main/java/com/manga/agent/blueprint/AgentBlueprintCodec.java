package com.manga.agent.blueprint;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.manga.agent.capability.CapabilityContractSupport;
import com.manga.agent.domain.CreativeRuntimeMessages;
import org.springframework.stereotype.Component;

import java.util.Objects;

/**
 * 统一负责 Blueprint 的规范化序列化、恢复和指纹复核。
 */
@Component
public class AgentBlueprintCodec {

    private final ObjectMapper canonicalMapper;

    public AgentBlueprintCodec(ObjectMapper objectMapper) {
        canonicalMapper = objectMapper.copy().enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);
    }

    /** 将 Blueprint 序列化为可写入 Step 的规范化 JSON。 */
    public String serialize(AgentBlueprint blueprint) {
        return json(blueprint);
    }

    /** 从 Step 快照恢复类型化 Blueprint，并复核自身指纹与 KernelKey。 */
    public AgentBlueprint deserialize(String snapshotJson) {
        try {
            AgentBlueprint blueprint = canonicalMapper.readValue(snapshotJson, AgentBlueprint.class);
            verifyFingerprint(blueprint);
            return blueprint;
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException(
                    CreativeRuntimeMessages.AGENT_BLUEPRINT_SERIALIZATION_FAILED, exception);
        }
    }

    /** 根据稳定材料计算 SHA-256 Blueprint 指纹。 */
    public String fingerprint(AgentBlueprintFingerprintMaterial material) {
        return CapabilityContractSupport.sha256(json(material));
    }

    /** 复核已恢复 Blueprint 的指纹，拒绝快照篡改或不完整写入。 */
    public void verifyFingerprint(AgentBlueprint blueprint) {
        String actual = fingerprint(materialOf(blueprint));
        if (!Objects.equals(actual, blueprint.fingerprint())
                || !Objects.equals(actual, blueprint.kernelKey().value())) {
            throw new IllegalStateException(CreativeRuntimeMessages.AGENT_BLUEPRINT_CONTRACT_MISMATCH);
        }
    }

    /** 从完整 Blueprint 提取不包含自指纹字段的稳定指纹材料。 */
    private AgentBlueprintFingerprintMaterial materialOf(AgentBlueprint blueprint) {
        return new AgentBlueprintFingerprintMaterial(
                blueprint.profileKey(),
                blueprint.profileVersion(),
                blueprint.model(),
                blueprint.systemPrompt(),
                blueprint.stableVariables(),
                blueprint.capabilities(),
                blueprint.permissionPolicy(),
                blueprint.maxTurns());
    }

    /** 执行规范化 JSON 序列化。 */
    private String json(Object value) {
        try {
            return canonicalMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException(
                    CreativeRuntimeMessages.AGENT_BLUEPRINT_SERIALIZATION_FAILED, exception);
        }
    }
}
