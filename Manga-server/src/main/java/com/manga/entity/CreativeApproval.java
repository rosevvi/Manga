package com.manga.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.manga.agent.changeset.ApprovalStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/** 保存审批决定、有效期、评论和真实审批人。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("manga_creative_approval")
public class CreativeApproval {
    /** 数据库主键。 */
    @TableId(type = IdType.AUTO)
    private Long id;
    /** 一对一关联不可变提案。 */
    private String changeSetId;
    /** 审批生命周期状态。 */
    private ApprovalStatus status;
    /** 审批有效期截止时间。 */
    private LocalDateTime expiresAt;
    /** 认证审批人主键；系统过期或取消时为空。 */
    private Long decidedBy;
    /** 最终决定时间。 */
    private LocalDateTime decidedAt;
    /** 审批评论或系统原因。 */
    private String comment;
    /** 创建时间。 */
    private LocalDateTime createdAt;
    /** 更新时间。 */
    private LocalDateTime updatedAt;
    /** 创建操作者。 */
    private String createdBy;
    /** 更新操作者。 */
    private String updatedBy;
}

