package com.manga.agent.blueprint;

import com.manga.agent.domain.AgentPermissionPolicy;
import com.manga.agent.profile.AgentExecutionPolicy;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.manga.agent.profile.AgentProfileKey;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 表示一个 Agent Step 可持久化、可恢复且不含请求数据和密钥的执行规格。
 */
public record AgentBlueprint(
        /** 对应共享 Kernel 的稳定缓存键。 */ AgentKernelKey kernelKey,
        /** 固定执行角色族；动态身份保存于 executionPolicy。 */ AgentProfileKey profileKey,
        /** Agent Profile 语义版本。 */ long profileVersion,
        /** 不含密钥的模型配置快照。 */ ModelSnapshot model,
        /** 编译后的完整系统提示词。 */ String systemPrompt,
        /** 只允许影响稳定 Kernel 构建的变量。 */ Map<String, String> stableVariables,
        /** 按稳定名称排序的精确能力白名单。 */ List<CapabilityManifest> capabilities,
        /** 本 Blueprint 允许的最高业务权限。 */ AgentPermissionPolicy permissionPolicy,
        /** 单次执行允许的最大推理轮次。 */ int maxTurns,
        /** Blueprint 全量规范化内容的 SHA-256 指纹。 */ String fingerprint,
        /** 编译时冻结的管理配置，空值保留原始快照协议。 */
        @JsonInclude(JsonInclude.Include.NON_NULL) AgentExecutionPolicy executionPolicy
) {

    /** 读取已有或内置规格时保留原始指纹协议，不补写默认字段。 */
    public AgentBlueprint(AgentKernelKey kernelKey, AgentProfileKey profileKey, long profileVersion,
            ModelSnapshot model, String systemPrompt, Map<String, String> stableVariables,
            List<CapabilityManifest> capabilities, AgentPermissionPolicy permissionPolicy, int maxTurns, String fingerprint) {
        this(kernelKey, profileKey, profileVersion, model, systemPrompt, stableVariables, capabilities,
                permissionPolicy, maxTurns, fingerprint, null);
    }

    /** 返回稳定配置身份，已有内置规格使用角色编码。 */
    public String profileId() { return Objects.isNull(executionPolicy) ? profileKey.code() : executionPolicy.profileId(); }

    /** 固化集合，避免运行中修改 Kernel 语义。 */
    public AgentBlueprint {
        stableVariables = Map.copyOf(stableVariables);
        capabilities = List.copyOf(capabilities);
    }
}
