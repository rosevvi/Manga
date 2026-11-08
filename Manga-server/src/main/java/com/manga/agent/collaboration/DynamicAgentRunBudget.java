package com.manga.agent.collaboration;

import java.math.BigDecimal;

/** 存入 Run.budgetJson 的冻结边界及消耗；修改默认配置不重置已有运行。 */
public record DynamicAgentRunBudget(
        /** 临时节点总数上限。 */ int maxAgents,
        /** 不同临时 KernelKey 上限。 */ int maxKernels,
        /** 临时推理轮数预留总额。 */ int maxReservedTurns,
        /** 单次最大轮数。 */ int maxTurns,
        /** 临时 Prompt 总字符预算。 */ int maxPromptCharacters,
        /** 规划费用上限，USD。 */ BigDecimal maxEstimatedUsd,
        /** 运维配置的每轮估值，为空禁止新建临时 Agent。 */ BigDecimal estimatedUsdPerTurn,
        /** 已消耗的临时轮数，按最大轮数保守预留。 */ int reservedTurns
) {
    /** 受理时冻结部署配置。 */
    public static DynamicAgentRunBudget freeze(DynamicAgentProperties value) {
        return new DynamicAgentRunBudget(value.maxAgents(), value.maxKernels(), value.maxReservedTurns(), value.maxTurns(),
                value.maxPromptCharacters(), value.maxEstimatedUsd(), value.estimatedUsdPerTurn(), 0);
    }
    /** 返回扣减后的不可变预算。 */
    public DynamicAgentRunBudget reserve(int turns) {
        return new DynamicAgentRunBudget(maxAgents, maxKernels, maxReservedTurns, maxTurns, maxPromptCharacters,
                maxEstimatedUsd, estimatedUsdPerTurn, reservedTurns + turns);
    }
}
