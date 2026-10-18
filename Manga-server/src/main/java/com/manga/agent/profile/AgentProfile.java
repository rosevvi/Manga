package com.manga.agent.profile;

import com.manga.agent.capability.CapabilityName;
import com.manga.agent.domain.AgentPermissionPolicy;

import java.util.Set;

/**
 * 描述不携带用户、项目或 Run 数据的长期 Agent 定义。
 */
public record AgentProfile(
        /** Profile 的稳定机器标识。 */
        AgentProfileKey key,
        /** Profile 语义版本。 */
        long version,
        /** 面向用户和运维界面的名称。 */
        String displayName,
        /** Profile 的职责说明。 */
        String description,
        /** 编译 Blueprint 使用的完整系统提示词。 */
        String systemPrompt,
        /** 允许注册到 Toolkit 的精确能力白名单。 */
        Set<CapabilityName> allowedCapabilities,
        /** Profile 允许的最高业务权限。 */
        AgentPermissionPolicy permissionPolicy,
        /** 单次执行允许的最大推理轮次。 */
        int maxTurns
) {

    /** 固化白名单，避免调用方修改 Profile。 */
    public AgentProfile {
        allowedCapabilities = Set.copyOf(allowedCapabilities);
    }
}
