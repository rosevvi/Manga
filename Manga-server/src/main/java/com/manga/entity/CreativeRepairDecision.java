package com.manga.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.manga.agent.collaboration.RepairAction;
import com.manga.agent.collaboration.RepairDecisionStatus;
import lombok.Data;
import java.time.LocalDateTime;

/** 保留候选、问题和剩余执行时间的用户修订检查点。 */
@Data
@TableName("manga_creative_repair_decision")
public class CreativeRepairDecision {
    /** 数据库主键。 */ @TableId(type = IdType.AUTO) private Long id;
    /** 对外确认单标识。 */ private String decisionId;
    /** 所属运行。 */ private String runId;
    /** 被暂停的父步骤。 */ private String parentStepId;
    /** 已完成的计划。 */ private String sourcePlanId;
    /** 产生问题的计划轮次。 */ private Integer roundNo;
    /** 不可变候选结果步骤。 */ private String candidateStepId;
    /** 不可变评审结果步骤，每份评审最多一张确认单。 */ private String reviewStepId;
    /** 评审问题列表 JSON。 */ private String issuesJson;
    /** 候选生成所依据的项目版本。 */ private Long baseProjectRevision;
    /** 可选章节主键。 */ private Long chapterId;
    /** 确认时需匹配的章节版本。 */ private Long baseChapterRevision;
    /** 暂停时剩余执行时间，单位毫秒。 */ private Long remainingExecutionMillis;
    /** 确认状态。 */ private RepairDecisionStatus status;
    /** 用户提交的命令。 */ private RepairAction action;
    /** 幂等决定请求标识。 */ private String requestId;
    /** 用户补充意见。 */ private String comment;
    /** 等待起点。 */ private LocalDateTime pausedAt;
    /** 等待过期时刻。 */ private LocalDateTime expiresAt;
    /** 决定发生时间。 */ private LocalDateTime decidedAt;
    /** 已创建的后继计划，支持重复确认返回同一结果。 */ private String repairPlanId;
    /** 记录创建时间。 */ private LocalDateTime createdAt;
    /** 记录更新时间。 */ private LocalDateTime updatedAt;
    /** 记录创建操作者。 */ private String createdBy;
    /** 最近修改操作者。 */ private String updatedBy;
}
