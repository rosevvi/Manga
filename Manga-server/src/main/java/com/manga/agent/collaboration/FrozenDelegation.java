package com.manga.agent.collaboration;

import com.manga.agent.blueprint.AgentBlueprint;
import java.util.List;

/** 工具提交时冻结的计划与 Worker Blueprint，恢复时不读取最新 Profile。 */
public record FrozenDelegation(
        /** 已校验的业务 DAG。 */ DelegatePlanRequest request,
        /** 按请求节点顺序冻结的无密钥执行规格。 */ List<AgentBlueprint> blueprints
) { }
