package com.manga.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Data;
import java.time.LocalDateTime;

/** 持久化协作运行预算；所有变更在所属 Run 行锁内完成。 */
@Data
@TableName("manga_creative_run_budget")
public class CreativeRunBudget {
    /** 数据库主键。 */
    @TableId(type = IdType.AUTO)
    private Long id;
    /** 预算归属的运行。 */
    private String runId;
    /** 包含汇合节点的步骤数量上限。 */
    private Integer maxSteps;
    /** 委派树深度上限。 */
    private Integer maxDepth;
    /** 同时执行步骤数量上限。 */
    private Integer maxParallel;
    /** 首次计划以外的修订轮数。 */
    private Integer maxRepairRounds;
    /** 模型执行次数上限。 */
    private Integer maxInvocations;
    /** 模型最大推理轮数的累计预留上限。 */
    private Integer maxReservedTurns;
    /** 已预留的模型执行次数，故障不退还。 */
    private Integer usedInvocations;
    /** 已预留的最大推理轮数。 */
    private Integer reservedTurns;
    /** 创建时间。 */
    private LocalDateTime createdAt;
    /** 最后更新时间。 */
    private LocalDateTime updatedAt;
    /** 创建操作者。 */
    private String createdBy;
    /** 最后更新操作者。 */
    private String updatedBy;
}

