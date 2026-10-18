package com.manga.agent.blueprint;

import com.manga.agent.domain.AgentPermissionPolicy;
import com.manga.agent.profile.AgentProfileKey;

import java.util.List;
import java.util.Map;

/**
 * 作为 Blueprint 指纹唯一输入的规范化稳定材料。
 */
public record AgentBlueprintFingerprintMaterial(
        /** Profile 标识。 */ AgentProfileKey profileKey,
        /** Profile 版本。 */ long profileVersion,
        /** 模型安全快照。 */ ModelSnapshot model,
        /** 完整系统提示词。 */ String systemPrompt,
        /** 只影响 Kernel 构建的稳定模板变量。 */ Map<String, String> stableVariables,
        /** 精确能力白名单。 */ List<CapabilityManifest> capabilities,
        /** Blueprint 允许的最高权限。 */ AgentPermissionPolicy permissionPolicy,
        /** 单次执行最大推理轮次。 */ int maxTurns
) {
}
