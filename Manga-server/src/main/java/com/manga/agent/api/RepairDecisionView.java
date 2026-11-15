package com.manga.agent.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.manga.agent.collaboration.RepairDecisionStatus;
import java.time.LocalDateTime;
import java.util.List;

/** 安全的修订确认预览，不暴露冻结提示词和模型凭据。 */
public record RepairDecisionView(
        /** 确认单标识。 */ String decisionId,
        /** 所属运行。 */ String runId,
        /** 原计划标识。 */ String sourcePlanId,
        /** 产生问题的轮次。 */ int round,
        /** 原候选结果引用。 */ String candidateStepId,
        /** 原评审结果引用。 */ String reviewStepId,
        /** 确认生命周期。 */ RepairDecisionStatus status,
        /** 用户应审阅的问题清单。 */ List<String> issues,
        /** 供用户预览的未批准候选。 */ JsonNode candidate,
        /** 用户补充意见。 */ String comment,
        /** 最迟确认时刻。 */ LocalDateTime expiresAt,
        /** 决定发生时间。 */ LocalDateTime decidedAt,
        /** 确认后创建的唯一计划。 */ String repairPlanId
) { }
