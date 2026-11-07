package com.manga.agent.profile;

/** 与 Blueprint 一起冻结的配置身份、选择策略和预算，不在恢复时读取最新 Profile。 */
public record AgentExecutionPolicy(
        /** 数据库稳定标识，角色枚举与专业 Agent 身份独立。 */ String profileId,
        /** 此版本的用户可见名称。 */ String displayName,
        /** 已发布定义的模型选择约束。 */ AgentModelPolicy model,
        /** 此版本的预算上限。 */ AgentBudgetLimits budget,
        /** 管理员只读测试快照；不能委派或提出业务变更。 */ boolean sandbox
) { }
