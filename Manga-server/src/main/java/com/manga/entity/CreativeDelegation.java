package com.manga.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.manga.agent.collaboration.DelegationStatus;
import lombok.Data;
import java.time.LocalDateTime;

/** 持久化协作计划检查点；所有变更在所属 Run 行锁内完成。 */
@Data
@TableName("manga_creative_delegation")
public class CreativeDelegation {
    /** 数据库主键。 */
    @TableId(type = IdType.AUTO)
    private Long id;
    /** 稳定协作计划标识。 */
    private String planId;
    /** 所属运行。 */
    private String runId;
    /** 暂停等待子图的父步骤。 */
    private String parentStepId;
    /** 可信工具调用标识，用于重放去重。 */
    private String triggerToolCallId;
    /** 提交计划时的执行权代次。 */
    private Long ownerEpoch;
    /** 父步骤的计划轮数，首次为一。 */
    private Integer roundNo;
    /** 用户可见计划名称。 */
    private String title;
    /** 持久化检查点状态。 */
    private DelegationStatus status;
    /** 冻结的请求及各 Worker Blueprint。 */
    private String frozenJson;
    /** 计划内容指纹。 */
    private String requestFingerprint;
    /** 物化后的汇合步骤。 */
    private String joinStepId;
    /** 创建时间。 */
    private LocalDateTime createdAt;
    /** 最后更新时间。 */
    private LocalDateTime updatedAt;
    /** 创建操作者。 */
    private String createdBy;
    /** 最后更新操作者。 */
    private String updatedBy;
}

