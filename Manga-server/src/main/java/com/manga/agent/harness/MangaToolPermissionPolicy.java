package com.manga.agent.harness;

import com.manga.agent.blueprint.AgentBlueprint;
import com.manga.agent.blueprint.CapabilityManifest;
import io.agentscope.core.permission.PermissionBehavior;
import io.agentscope.core.permission.PermissionContextState;
import io.agentscope.core.permission.PermissionMode;
import io.agentscope.core.permission.PermissionRule;

/** SDK 内只放行冻结白名单中的能力，真正业务批准仍由 Manga 审批事务处理。 */
public final class MangaToolPermissionPolicy {
    /** 规则来源用于 SDK 状态审计，不接受模型指定。 */
    private static final String RULE_SOURCE = "manga:blueprint";

    private MangaToolPermissionPolicy() { }

    /** 显式授权可执行的读取/草稿工具，其他工具直接拒绝，避免 SDK 内再次挂起人工询问。 */
    public static PermissionContextState forBlueprint(AgentBlueprint blueprint) {
        PermissionContextState.Builder builder = PermissionContextState.builder().mode(PermissionMode.DONT_ASK);
        for (CapabilityManifest capability : blueprint.capabilities()) {
            if (blueprint.permissionPolicy().allows(capability.requiredPermission())) {
                String name = capability.name().code();
                builder.addAllowRule(name, new PermissionRule(name, null, PermissionBehavior.ALLOW, RULE_SOURCE));
            }
        }
        return builder.build();
    }
}
