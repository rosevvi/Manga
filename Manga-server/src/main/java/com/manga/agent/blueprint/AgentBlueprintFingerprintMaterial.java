package com.manga.agent.blueprint;

import com.manga.agent.domain.AgentPermissionPolicy;
import com.manga.agent.profile.AgentExecutionPolicy;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.manga.agent.profile.AgentProfileKey;

import java.util.List;
import java.util.Map;

/**
 * 作为 Blueprint 指纹唯一输入的规范化稳定材料。
 */
public record AgentBlueprintFingerprintMaterial(
        /** 固定执行角色族。 */ AgentProfileKey profileKey,
        /** Profile 版本。 */ long profileVersion,
        /** 模型安全快照。 */ ModelSnapshot model,
        /** 完整系统提示词。 */ String systemPrompt,
        /** 只影响 Kernel 构建的稳定模板变量。 */ Map<String, String> stableVariables,
        /** 精确能力白名单。 */ List<CapabilityManifest> capabilities,
        /** Blueprint 允许的最高权限。 */ AgentPermissionPolicy permissionPolicy,
        /** 单次执行最大推理轮次。 */ int maxTurns,
        /** 管理配置参与指纹；旧规格为空时保持原规范化内容。 */
        @JsonInclude(JsonInclude.Include.NON_NULL) AgentExecutionPolicy executionPolicy
) {
    /** 保留内置初始化及已有快照的指纹材料构造方式。 */
    public AgentBlueprintFingerprintMaterial(AgentProfileKey profileKey, long profileVersion, ModelSnapshot model,
            String systemPrompt, Map<String, String> stableVariables, List<CapabilityManifest> capabilities,
            AgentPermissionPolicy permissionPolicy, int maxTurns) {
        this(profileKey, profileVersion, model, systemPrompt, stableVariables, capabilities, permissionPolicy, maxTurns, null);
    }
}
