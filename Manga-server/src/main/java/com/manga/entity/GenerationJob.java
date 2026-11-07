package com.manga.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.manga.agent.generation.GenerationJobStatus;
import com.manga.agent.generation.GenerationProviderKey;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;
import java.time.LocalDateTime;

/** 外部媒体作业持久化聚合；结果与执行权独立于 Agent Harness。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("manga_generation_job")
public class GenerationJob {
    /** 数据库主键。 */
    @TableId(type = IdType.AUTO)
    private Long id;
    /** 平台稳定作业标识，也是提交意图幂等键。 */
    private String jobId;
    /** 来源运行。 */
    private String runId;
    /** 等待作业的步骤。 */
    private String stepId;
    /** 批准的唯一图片提案。 */
    private String changeSetId;
    /** 作业归属用户。 */
    private Long userId;
    /** 归属项目。 */
    private Long projectId;
    /** 目标镜头。 */
    private Long shotId;
    /** 供应商路由。 */
    private GenerationProviderKey provider;
    /** 用户供应商配置，Fake 为空。 */
    private Long configId;
    /** 不含凭据的端点快照，防止审批后被换址。 */
    @ToString.Exclude
    private String configSnapshotJson;
    /** 冻结且已批准的生成参数。 */
    @ToString.Exclude
    private String requestJson;
    /** 平台作业状态。 */
    private GenerationJobStatus status;
    /** 异步供应商作业标识，同步 Images API 为空。 */
    private String providerJobId;
    /** 真实百分比，未知时为空。 */
    private Integer progress;
    /** 已撤销结果采用权；不承诺同步供应商停止计费。 */
    private Boolean cancelRequested;
    /** 已生成 PNG 暂存数据；转存后清除。 */
    @ToString.Exclude
    private String resultBase64;
    /** 公开错误编码。 */
    private String errorCode;
    /** 脱敏错误摘要。 */
    private String errorMessage;
    /** 作业 worker 所属实例。 */
    private String ownerInstanceId;
    /** 执行权围栏代次。 */
    private Long ownerEpoch;
    /** worker 执行权截止。 */
    private LocalDateTime leaseUntil;
    /** 状态读取或转存尝试次数。 */
    private Integer attempts;
    /** 下次允许处理时间。 */
    private LocalDateTime nextActionAt;
    /** 首次登记网络提交意图时间。 */
    private LocalDateTime submittedAt;
    /** 平台终态时间。 */
    private LocalDateTime finishedAt;
    /** 主动核对未知提交的用户。 */
    private Long reconciledBy;
    /** 核对时间。 */
    private LocalDateTime reconciledAt;
    /** 人工核对说明，不是供应商取消确认。 */
    private String reconciliationNote;
    /** 创建时间。 */
    private LocalDateTime createdAt;
    /** 更新时间。 */
    private LocalDateTime updatedAt;
    /** 创建操作者。 */
    private String createdBy;
    /** 最后更新操作者。 */
    private String updatedBy;
}

