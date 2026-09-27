package com.manga.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.manga.agent.domain.AgentPermissionPolicy;
import com.manga.agent.domain.CreativeOperationType;
import com.manga.agent.domain.CreativeResourceType;
import com.manga.agent.domain.CreativeRunStatus;
import com.manga.agent.domain.CreativeRunTriggerSource;
import com.manga.agent.domain.CreativeWorkflowType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 表示一次由会话或页面操作触发的持久化创作运行。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("manga_creative_run")
public class CreativeRun {

    /** 数据库主键。 */
    @TableId(type = IdType.AUTO)
    private Long id;
    /** 对外暴露的运行唯一标识。 */
    private String runId;
    /** 用户维度的启动幂等标识。 */
    private String requestId;
    /** 启动请求语义的 SHA-256 指纹。 */
    private String requestFingerprint;
    /** 可选的所属会话标识，手动触发时为空。 */
    private String conversationId;
    /** 发起运行的用户主键。 */
    private Long userId;
    /** 运行所属项目主键。 */
    private Long projectId;
    /** 创建时固化的执行引擎版本。 */
    private String engineVersion;
    /** 创建时固化的工作流类型。 */
    private CreativeWorkflowType workflowType;
    /** 创建时固化的工作流版本。 */
    private String workflowVersion;
    /** 创建时固化的最高权限策略。 */
    private AgentPermissionPolicy permissionPolicy;
    /** 可选的运行预算与资源限制快照。 */
    private String budgetJson;
    /** 运行触发来源。 */
    private CreativeRunTriggerSource triggerSource;
    /** 本次运行执行的稳定业务操作。 */
    private CreativeOperationType operationType;
    /** 可选的直接目标资源类型。 */
    private CreativeResourceType targetType;
    /** 可选的直接目标资源主键。 */
    private Long targetId;
    /** 当前运行状态。 */
    private CreativeRunStatus status;
    /** 仅活跃会话 Run 使用的并发唯一键。 */
    private String activeConversationId;
    /** 后续执行器领取运行时写入的实例标识。 */
    private String ownerInstanceId;
    /** 每次重新领取执行权时递增的租约代次。 */
    private Long ownerEpoch;
    /** 当前执行租约截止时间。 */
    private LocalDateTime leaseUntil;
    /** 整次运行允许执行的最晚时间。 */
    private LocalDateTime deadlineAt;
    /** 下一个运行事件序号。 */
    private Long nextSequence;
    /** 唯一终态事件序号。 */
    private Long terminalSequence;
    /** 首次收到取消请求的时间。 */
    private LocalDateTime cancelRequestedAt;
    /** 终态错误机器编码。 */
    private String errorCode;
    /** 脱敏后的终态错误描述。 */
    private String errorMessage;
    /** 实际开始执行时间。 */
    private LocalDateTime startedAt;
    /** 进入终态时间。 */
    private LocalDateTime finishedAt;
    /** 记录创建时间。 */
    private LocalDateTime createdAt;
    /** 记录最后更新时间。 */
    private LocalDateTime updatedAt;
    /** 记录创建操作者。 */
    private String createdBy;
    /** 记录最后更新操作者。 */
    private String updatedBy;
}
