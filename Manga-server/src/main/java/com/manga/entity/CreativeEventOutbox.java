package com.manga.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.manga.agent.domain.CreativeOutboxStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 记录已提交事件尚未完成的实时唤醒投递。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("manga_creative_event_outbox")
public class CreativeEventOutbox {

    /** 数据库主键。 */
    @TableId(type = IdType.AUTO)
    private Long id;
    /** 所属 Run 标识。 */
    private String runId;
    /** 所属事件序号。 */
    private Long sequenceNo;
    /** 投递状态。 */
    private CreativeOutboxStatus status;
    /** 当前批量领取实例标识。 */
    private String claimOwner;
    /** 当前批量领取截止时间。 */
    private LocalDateTime claimUntil;
    /** 下一次允许投递时间。 */
    private LocalDateTime nextAttemptAt;
    /** 投递完成时间。 */
    private LocalDateTime publishedAt;
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
