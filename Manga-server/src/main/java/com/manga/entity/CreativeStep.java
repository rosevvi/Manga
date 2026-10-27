package com.manga.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.manga.agent.domain.CreativeStepStatus;
import com.manga.agent.domain.CreativeStepType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 表示可独立领取、续租、恢复和收敛的创作步骤。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("manga_creative_step")
public class CreativeStep {

    /** 数据库主键。 */
    @TableId(type = IdType.AUTO)
    private Long id;
    /** 对外稳定的步骤标识。 */
    private String stepId;
    /** 所属 Run 标识。 */
    private String runId;
    /** 可选父步骤标识。 */
    private String parentStepId;
    /** 执行器路由使用的步骤类型。 */
    private CreativeStepType stepType;
    /** 当前步骤状态。 */
    private CreativeStepStatus status;
    /** 每次重新调度递增的版本号。 */
    private Long scheduleVersion;
    /** 当前执行权所属实例。 */
    private String ownerInstanceId;
    /** 当前执行权的围栏代次。 */
    private Long ownerEpoch;
    /** 当前执行权租约截止时间。 */
    private LocalDateTime leaseUntil;
    /** 累计成功领取次数。 */
    private Integer attemptNo;
    /** 最大允许领取次数。 */
    private Integer maxAttempts;
    /** 创建时固化的输入 JSON。 */
    private String inputJson;
    /** 开始生成前冻结的项目版本，防止提案覆盖生成期间的人工修改。 */
    private Long baseProjectRevision;
    /** 审批持久化恢复命令；空值表示尚未处理审批。 */
    private String approvalResultJson;
    /** Agent Step 执行前固化的不含密钥 Blueprint 快照。 */
    private String blueprintSnapshotJson;
    /** Blueprint 快照的 SHA-256 指纹。 */
    private String blueprintFingerprint;
    /** AgentScope 状态恢复使用的会话标识。 */
    private String stateSessionId;
    /** 成功时固化的输出 JSON。 */
    private String outputJson;
    /** 失败错误机器编码。 */
    private String errorCode;
    /** 脱敏后的失败摘要。 */
    private String errorMessage;
    /** 首次开始执行时间。 */
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
