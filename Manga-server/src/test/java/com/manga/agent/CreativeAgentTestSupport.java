package com.manga.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.manga.agent.blueprint.AgentBlueprint;
import com.manga.agent.blueprint.AgentBlueprintCodec;
import com.manga.agent.blueprint.AgentBlueprintFingerprintMaterial;
import com.manga.agent.blueprint.AgentKernelKey;
import com.manga.agent.blueprint.CompiledAgentBlueprint;
import com.manga.agent.blueprint.ModelSnapshot;
import com.manga.agent.domain.AgentPermissionPolicy;
import com.manga.agent.domain.CreativeOperationType;
import com.manga.agent.domain.CreativeResourceType;
import com.manga.agent.domain.CreativeRunStatus;
import com.manga.agent.domain.CreativeRunTriggerSource;
import com.manga.agent.domain.CreativeWorkflowType;
import com.manga.agent.profile.AgentProfileKey;
import com.manga.common.enums.AiProviderType;
import com.manga.entity.CreativeRun;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** 为数据库集成测试提供不调用外部供应商的有效冻结规格和运行记录。 */
public final class CreativeAgentTestSupport {
    /** 创建可由真实 Codec 验证的测试规格，模型调用由测试替身提供。 */
    public static CompiledAgentBlueprint blueprint() {
        AgentBlueprintCodec codec = new AgentBlueprintCodec(new ObjectMapper());
        AgentBlueprintFingerprintMaterial material = new AgentBlueprintFingerprintMaterial(
                AgentProfileKey.MANGA_DIRECTOR, 1L,
                new ModelSnapshot(1L, AiProviderType.OPENAI_COMPATIBLE,
                        "https://model.example.test/v1", "test-model", "test-fingerprint", 32768),
                "test director", Map.of(), List.of(), AgentPermissionPolicy.READ_ONLY, 12);
        String fingerprint = codec.fingerprint(material);
        AgentBlueprint blueprint = new AgentBlueprint(new AgentKernelKey(fingerprint),
                material.profileKey(), material.profileVersion(), material.model(), material.systemPrompt(),
                material.stableVariables(), material.capabilities(), material.permissionPolicy(), material.maxTurns(), fingerprint);
        return new CompiledAgentBlueprint(blueprint, codec.serialize(blueprint));
    }
    /** 创建满足真实数据库约束、可用于状态生命周期验证的手动运行。 */
    public static CreativeRun run(long userId) {
        return CreativeRun.builder().runId(UUID.randomUUID().toString()).requestId(UUID.randomUUID().toString())
                .requestFingerprint("0".repeat(64)).userId(userId).triggerSource(CreativeRunTriggerSource.MANUAL)
                .operationType(CreativeOperationType.PARSE_SCRIPT).workflowType(CreativeWorkflowType.SCRIPT_PARSE)
                .targetType(CreativeResourceType.SCRIPT).targetId(1L).engineVersion("test-v3").workflowVersion("v1")
                .permissionPolicy(AgentPermissionPolicy.READ_ONLY).status(CreativeRunStatus.QUEUED)
                .ownerEpoch(0L).deadlineAt(LocalDateTime.now().plusMinutes(30)).nextSequence(1L)
                .projectedThroughSequence(0L).stateGeneration(1L).createdBy("test:memory").updatedBy("test:memory").build();
    }
    /** 禁止实例化测试工厂。 */
    private CreativeAgentTestSupport() { }
}
