package com.manga.agent.event;

import com.manga.agent.domain.CreativeToolStatus;
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
        /** 实际工具结束时间，旧事件可为空。 */ LocalDateTime finishedAt
) {
    /** 保持已有写入方和旧事件兼容。 */
    public ToolExecutionEventPayload(String toolCallId, String toolName, CreativeToolStatus status) {
        this(toolCallId, toolName, status, 1, null, null, null, null, null);
    }
}
