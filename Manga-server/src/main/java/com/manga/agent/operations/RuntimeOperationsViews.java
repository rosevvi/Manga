package com.manga.agent.operations;

import com.manga.entity.ModelCallLedger;
import com.manga.entity.CreativeRunArchive;
import com.manga.entity.ProviderCallGate;
import java.time.LocalDateTime;

/** 运维查询的稳定输出契约，不直接暴露持久化实体。 */
public final class RuntimeOperationsViews {
    /** 配置门禁的公开诊断摘要。 */
    public record Gate(
            /** 配置主键。 */ long configId,
            /** 当前分钟窗开始时间，UTC。 */ LocalDateTime windowStartedAt,
            /** 窗内已受理调用数。 */ int windowRequests,
            /** 连续可重试故障数。 */ int consecutiveFailures,
            /** 熔断冷却截止时间，UTC；空表示闭合。 */ LocalDateTime openUntil
    ) {
        /** 只映射门禁诊断字段，不暴露持久化审计元数据。 */
        public static Gate from(ProviderCallGate row) {
            return new Gate(row.getConfigId(), row.getWindowStartedAt(), row.getWindowRequests(), row.getConsecutiveFailures(), row.getOpenUntil());
        }
    }
    /** 调用与用量诊断视图，不含连接地址、Prompt、凭据或内部门禁配置。 */
    public record Call(
            /** 游标 ID。 */ long id,
            /** 稳定调用标识。 */ String callId,
            /** 所属 Run。 */ String runId,
            /** 所属 Step。 */ String stepId,
            /** 开始调用时的 owner 围栏。 */ long ownerEpoch,
            /** 配置标识。 */ long configId,
            /** 冻结模型名。 */ String modelName,
            /** 冻结规格指纹。 */ String blueprintFingerprint,
            /** 调用状态，失败时用量可能不完整。 */ ModelCallStatus status,
            /** 是否收到供应商 usage。 */ boolean usageReported,
            /** 累计输入 token。 */ Long inputTokens,
            /** 累计输出 token。 */ Long outputTokens,
            /** 输入中的缓存命中 token。 */ Long cachedTokens,
            /** 输出中的推理 token。 */ Long reasoningTokens,
            /** 安全失败类型。 */ String failureType,
            /** 调用开始时间，UTC。 */ LocalDateTime startedAt,
            /** 调用结束时间，UTC。 */ LocalDateTime finishedAt
    ) {
        /** 将实体映射为安全 API 契约。 */
        public static Call from(ModelCallLedger row) {
            return new Call(row.getId(), row.getCallId(), row.getRunId(), row.getStepId(), row.getOwnerEpoch(), row.getConfigId(),
                    row.getModelName(), row.getBlueprintFingerprint(), row.getStatus(), Boolean.TRUE.equals(row.getUsageReported()),
                    row.getInputTokens(), row.getOutputTokens(), row.getCachedTokens(), row.getReasoningTokens(),
                    row.getFailureType(), row.getStartedAt(), row.getFinishedAt());
        }
    }
    /** 原始审计包下载契约，payload 不重新编码以保持 SHA-256 有效。 */
    public record Archive(
            /** 来源 Run。 */ String runId,
            /** 审计格式版本。 */ int schemaVersion,
            /** 原始 JSON 字符串，含用户内容。 */ String payloadJson,
            /** 原始 payload 的 SHA-256。 */ String sha256
    ) {
        /** 仅在校验通过后映射给管理员。 */
        public static Archive from(CreativeRunArchive row) { return new Archive(row.getRunId(), row.getSchemaVersion(), row.getPayloadJson(), row.getSha256()); }
    }
    private RuntimeOperationsViews() { }
}
