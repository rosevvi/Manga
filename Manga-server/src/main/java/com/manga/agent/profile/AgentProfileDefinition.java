package com.manga.agent.profile;

import com.manga.agent.capability.CapabilityName;
import com.manga.agent.domain.AgentPermissionPolicy;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.Set;

/** 管理员可编辑的纯数据定义；不能上传执行代码、URL 工具或自定义 Java 类。 */
public record AgentProfileDefinition(
        /** 当前定义协议版本。 */ @Min(1) @Max(1) int schemaVersion,
        /** 固定执行角色族，决定结果 schema 与业务权限上限。 */ @NotNull AgentProfileKey role,
        /** 当前版本名称。 */ @NotBlank @Size(max = 120) String displayName,
        /** 对用户及 Director 展示的职责说明。 */ @NotBlank @Size(max = 2000) String description,
        /** 本版本系统提示词。 */ @NotBlank @Size(max = 24000) String systemPrompt,
        /** 已注册能力白名单；Worker 平台结果工具由运行时添加。 */ @NotNull @Size(max = 32) Set<@NotNull CapabilityName> capabilities,
        /** 最高业务权限，不能超过角色上限。 */ @NotNull AgentPermissionPolicy permissionPolicy,
        /** 单次推理轮数上限。 */ @Min(1) @Max(32) int maxTurns,
        /** 不含密钥的模型策略。 */ @NotNull @Valid AgentModelPolicy model,
        /** 发布后冻结的预算上限。 */ @NotNull @Valid AgentBudgetLimits budget
) {
    /** 将已校验的版本变成运行时定义，稳定身份从聚合提供。 */
    public AgentProfile profile(String profileId, long version) {
        return new AgentProfile(role, version, displayName, description, systemPrompt, capabilities,
                permissionPolicy, maxTurns, new AgentExecutionPolicy(profileId, displayName, model, budget, false));
    }
}
