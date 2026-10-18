package com.manga.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.manga.agent.domain.CreativeDispatchOutboxStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 记录事务提交后仍需可靠投递的 Step 调度信号。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("manga_creative_dispatch_outbox")
public class CreativeDispatchOutbox {

    /** 数据库主键。 */
    @TableId(type = IdType.AUTO)
    private Long id;
    /** 待调度 Step 标识。 */
    private String stepId;
    /** 待投递的调度版本。 */
    private Long scheduleVersion;
    /** 当前投递状态。 */
    private CreativeDispatchOutboxStatus status;
    /** 当前领取记录的实例标识。 */
    private String claimOwner;
    /** 当前领取截止时间。 */
    private LocalDateTime claimUntil;
    /** 下一次允许投递时间。 */
    private LocalDateTime nextAttemptAt;
    /** 完成投递时间。 */
    private LocalDateTime dispatchedAt;
    /** 累计投递次数。 */
    private Integer attempts;
    /** 最近一次投递错误摘要。 */
    private String lastError;
    /** 记录创建时间。 */
    private LocalDateTime createdAt;
    /** 记录最后更新时间。 */
    private LocalDateTime updatedAt;
    /** 记录创建操作者。 */
    private String createdBy;
    /** 记录最后更新操作者。 */
    private String updatedBy;
}
