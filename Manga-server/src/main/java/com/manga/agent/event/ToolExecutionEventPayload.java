package com.manga.agent.event;

import com.manga.agent.domain.CreativeToolStatus;
import com.manga.agent.capability.CapabilityTarget;
import java.time.LocalDateTime;

/**
 * 描述一次 Agent 工具调用的公开状态，不包含工具参数或返回正文。
 */
public record ToolExecutionEventPayload(
        /** AgentScope 为本次工具调用分配的标识。 */ String toolCallId,
        /** Blueprint 白名单中的稳定工具名称。 */ String toolName,
        /** 与 AgentScope 解耦的公开工具状态。 */ CreativeToolStatus status,
        /** 载荷版本，旧事件为空。 */ Integer payloadVersion,
        /** 原始执行代次。 */ Long ownerEpoch,
        /** 仅包含资源名称和标识的输入摘要。 */ String inputSummary,
        /** 仅包含标题和数量的领域结果摘要。 */ String resultSummary,
        /** 实际工具开始时间，旧事件可为空。 */ LocalDateTime startedAt,
        /** 实际工具结束时间，旧事件可为空。 */ LocalDateTime finishedAt,
        /** 相同输入的稳定指纹，不携带原始参数。 */ String inputFingerprint,
        /** 是否复用当前执行的读取结果。 */ Boolean reused,
        /** 相同输入的调用次数。 */ Integer callCount,
        /** 目标明确的工具标题。 */ String actionLabel,
        /** 类型化目标引用。 */ CapabilityTarget target,
        /** 脱敏失败原因。 */ String failureReason
) {
    /** 版本二调用方无需提前提供新增可选字段。 */
    public ToolExecutionEventPayload(String toolCallId, String toolName, CreativeToolStatus status, Integer payloadVersion,
            Long ownerEpoch, String inputSummary, String resultSummary, LocalDateTime startedAt, LocalDateTime finishedAt) {
        this(toolCallId, toolName, status, payloadVersion, ownerEpoch, inputSummary, resultSummary, startedAt, finishedAt,
                null, null, null, null, null, null);
    }
    /** 保持已有写入方和旧事件兼容。 */
    public ToolExecutionEventPayload(String toolCallId, String toolName, CreativeToolStatus status) {
        this(toolCallId, toolName, status, 1, null, null, null, null, null);
    }
}
