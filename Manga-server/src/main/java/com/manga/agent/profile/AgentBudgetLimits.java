package com.manga.agent.profile;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/** Profile 的预算上限；实际运行取平台默认值与 Profile 上限的较小值。 */
public record AgentBudgetLimits(
        /** Run 步骤数上限。 */ @Min(1) @Max(128) int maxSteps,
        /** 最大委派深度。 */ @Min(1) @Max(8) int maxDepth,
        /** 同 Run 最大并发步骤数。 */ @Min(1) @Max(8) int maxParallel,
        /** 首轮计划以外的修订轮数。 */ @Min(0) @Max(8) int maxRepairRounds,
        /** 根运行的模型调用次数上限；Worker 中限制本步骤的模型执行次数。 */ @Min(1) @Max(128) int maxInvocations,
        /** 根运行累计预留轮数上限；Worker 中限制本步骤的累计轮数。 */ @Min(1) @Max(4096) int maxReservedTurns
) { }
