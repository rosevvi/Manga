package com.manga.agent.blueprint;

import com.manga.agent.domain.AgentPermissionPolicy;
import com.manga.agent.profile.AgentProfileKey;

import java.util.List;
import java.util.Map;

/**
 * 表示一个 Agent Step 可持久化、可恢复且不含请求数据和密钥的执行规格。
 */
public record AgentBlueprint(
        /** 对应共享 Kernel 的稳定缓存键。 */ AgentKernelKey kernelKey,
        /** Agent Profile 稳定机器标识。 */ AgentProfileKey profileKey,
        /** Agent Profile 语义版本。 */ long profileVersion,
        /** 不含密钥的模型配置快照。 */ ModelSnapshot model,
        /** 编译后的完整系统提示词。 */ String systemPrompt,
        /** 只允许影响稳定 Kernel 构建的变量。 */ Map<String, String> stableVariables,
        /** 按稳定名称排序的精确能力白名单。 */ List<CapabilityManifest> capabilities,
        /** 本 Blueprint 允许的最高业务权限。 */ AgentPermissionPolicy permissionPolicy,
        /** 单次执行允许的最大推理轮次。 */ int maxTurns,
        /** Blueprint 全量规范化内容的 SHA-256 指纹。 */ String fingerprint
) {

    /** 固化集合，避免运行中修改 Kernel 语义。 */
    public AgentBlueprint {
        stableVariables = Map.copyOf(stableVariables);
        capabilities = List.copyOf(capabilities);
    }
}
