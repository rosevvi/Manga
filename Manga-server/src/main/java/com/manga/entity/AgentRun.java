package com.manga.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.manga.agent.AgentRunStatus;
import com.manga.agent.domain.CreativeOperationType;
import com.manga.agent.domain.CreativeResourceType;
import com.manga.agent.domain.CreativeRunTriggerSource;
import com.manga.agent.domain.CreativeRuntimeType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/** 表示一次由会话或手动操作触发的创作执行。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("manga_creative_run")
public class AgentRun {
    /** 数据库主键。 */
    @TableId(type = IdType.AUTO)
    private Long id;
    /** 对外暴露的运行唯一标识。 */
    private String runId;
    /** 可选的助手会话标识，手动触发时允许为空。 */
    private String conversationId;
    /** 发起运行的用户主键。 */
    private Long userId;
    /** 运行所属项目主键。 */
    private Long projectId;
    /** 创建 Run 时选定且运行期间不可切换的运行时类型。 */
    private CreativeRuntimeType runtimeType;
    /** 运行由会话或用户页面操作触发。 */
    private CreativeRunTriggerSource triggerSource;
    /** 本次运行执行的稳定业务操作类型。 */
    private CreativeOperationType operationType;
    /** 可选的直接目标资源类型。 */
    private CreativeResourceType targetType;
    /** 可选的直接目标资源主键。 */
    private Long targetId;
    /** 本次执行使用的 Agent 定义键。 */
    private String agentKey;
    /** 提交时使用的 AI 供应商配置主键。 */
    private Long providerConfigId;
    /** 提交时使用的模型编码。 */
    private String modelCode;
    /** 不可变 Kernel 配置的 SHA-256 指纹。 */
    private String kernelFingerprint;
    /** 不可变 Kernel 配置快照。 */
    private String kernelSnapshotJson;
    /** AgentScope 状态会话标识。 */
    private String stateSessionId;
    /** 运行当前状态。 */
    private AgentRunStatus status;
    /** 仅在会话 Run 活跃期间存在，用于阻止同一会话并发执行。 */
    private String activeConversationId;
    /** 当前持有执行租约的服务实例。 */
    private String ownerInstanceId;
    /** 每次重新领取执行权时递增的租约代次。 */
    private Long ownerEpoch;
    /** 当前执行租约截止时间。 */
    private LocalDateTime leaseUntil;
    /** 整次运行允许执行的最晚时间。 */
    private LocalDateTime deadlineAt;
    /** 下一个运行事件序号。 */
    private Long nextSequence;
    /** 终态错误编码。 */
    private String errorCode;
    /** 脱敏后的终态错误描述。 */
    private String errorMessage;
    /** 运行开始时间。 */
    private LocalDateTime startedAt;
    /** 运行结束时间。 */
    private LocalDateTime finishedAt;
    /** 记录创建时间。 */
    private LocalDateTime createdAt;
    /** 记录最后更新时间。 */
    private LocalDateTime updatedAt;
}
