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
        /** 要编译的内置 Agent Profile。 */
        AgentProfileKey profileKey,
        /** 本次 Run 允许的最高业务权限。 */
        AgentPermissionPolicy permissionPolicy,
        /** 可选的指定模型配置；为空时使用用户默认配置。 */
        Long modelConfigId,
        /** 不含用户或项目请求数据的稳定模板变量。 */
        Map<String, String> stableVariables
) {

    /** 固化稳定变量并将空值规范为空 Map。 */
    public AgentBlueprintCompileRequest {
        stableVariables = Objects.isNull(stableVariables) ? Map.of() : Map.copyOf(stableVariables);
    }
}
