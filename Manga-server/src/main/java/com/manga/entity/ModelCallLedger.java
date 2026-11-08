package com.manga.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;
import java.math.BigDecimal;
import com.manga.agent.operations.ModelCallStatus;

/** 模型调用和已报告 token 的不可重复结算记录。 */
@Data
@TableName("manga_model_call_ledger")
public class ModelCallLedger {
    /** 数据库主键。 */
    @TableId(type = IdType.AUTO)
    private Long id;
    /** 每次实际 Model.stream 订阅的唯一调用标识。 */
    private String callId;
    /** 归属 Run，关联完整调用链。 */
    private String runId;
    /** 归属 Step。 */
    private String stepId;
    /** 开始调用时的围栏代次。 */
    private Long ownerEpoch;
    /** 模型配置主键，不保存连接地址或密钥。 */
    private Long configId;
    /** 被冻结的模型名。 */
    private String modelName;
    /** 来源 Blueprint 指纹。 */
    private String blueprintFingerprint;
    /** 持久化调用状态。 */
    private ModelCallStatus status;
    /** 供应商是否返回过用量；失败时可能仅为部分值。 */
    private Boolean usageReported;
    /** 最近累计输入 token，缓存命中是其子集。 */
    private Long inputTokens;
    /** 最近累计输出 token，包含推理输出。 */
    private Long outputTokens;
    /** 最近累计缓存命中 token。 */
    private Long cachedTokens;
    /** 推理 token 诊断量，不重复加到输出费用。 */
    private Long reasoningTokens;
    /** 额外缓存创建 token，当前费率契约不覆盖时保持费用未定。 */
    private Long cacheCreationTokens;
    /** 额外工具 Prompt token，当前费率契约不覆盖时保持费用未定。 */
    private Long toolUsePromptTokens;
    /** 冻结的标准输入单价，USD/百万 token；空为未定价。 */
    private BigDecimal inputPrice;
    /** 冻结的缓存输入单价，USD/百万 token。 */
    private BigDecimal cachedInputPrice;
    /** 冻结的输出单价，USD/百万 token。 */
    private BigDecimal outputPrice;
    /** 规划预留 USD，受理即消耗不退还。 */
    private BigDecimal reservedUsd;
    /** 按已报告 token 计算的 USD，非供应商账单；空为未知或未定价。 */
    private BigDecimal calculatedUsd;
    /** 人工核对供应商账单后确认的 USD，空表示尚未对账。 */
    private BigDecimal settledUsd;
    /** 认证对账管理员主键。 */
    private Long settledBy;
    /** 对账时间，UTC。 */
    private LocalDateTime settledAt;
    /** 对账依据或账单编号，不填写凭据。 */
    private String settlementNote;
    /** 失败类型编码，仅保存类名，不保存异常消息。 */
    private String failureType;
    /** 调用持久化受理时间，UTC。 */
    private LocalDateTime startedAt;
    /** 容量租约及请求截止时刻，UTC。 */
    private LocalDateTime expiresAt;
    /** 调用关闭时间，UTC。 */
    private LocalDateTime finishedAt;
    /** 创建时间。 */
    private LocalDateTime createdAt;
    /** 最后更新时间。 */
    private LocalDateTime updatedAt;
    /** 创建操作者。 */
    private String createdBy;
    /** 更新操作者。 */
    private String updatedBy;
}
