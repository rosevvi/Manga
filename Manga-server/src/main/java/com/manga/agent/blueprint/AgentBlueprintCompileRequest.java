package com.manga.agent.blueprint;

import com.manga.agent.domain.AgentPermissionPolicy;
import com.manga.agent.profile.AgentProfileKey;

import java.util.Map;
import java.util.Objects;

/**
 * 描述编译 Agent Blueprint 所需的稳定选择参数。
 */
public record AgentBlueprintCompileRequest(
        /** 用于查找用户模型配置的认证用户主键，不写入 Blueprint。 */
        long userId,
        /** 未显式选择 profileId 时使用的内置操作绑定。 */
        AgentProfileKey profileKey,
        /** 本次 Run 允许的最高业务权限。 */
        AgentPermissionPolicy permissionPolicy,
        /** 可选的指定模型配置；为空时使用用户默认配置。 */
        Long modelConfigId,
        /** 不含用户或项目请求数据的稳定模板变量。 */
        Map<String, String> stableVariables,
        /** 用户明确选择的数据库 Agent 标识；空值使用内置操作绑定。 */ String profileId
) {

    /** 未显式选择专业 Agent 时使用内置操作绑定。 */
    public AgentBlueprintCompileRequest(long userId, AgentProfileKey profileKey, AgentPermissionPolicy permissionPolicy,
            Long modelConfigId, Map<String, String> stableVariables) {
        this(userId, profileKey, permissionPolicy, modelConfigId, stableVariables, null);
    }

    /** 固化稳定变量并将空值规范为空 Map。 */
    public AgentBlueprintCompileRequest {
        stableVariables = Objects.isNull(stableVariables) ? Map.of() : Map.copyOf(stableVariables);
    }
}
