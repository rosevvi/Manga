package com.manga.agent.event;

import com.manga.agent.domain.CreativeToolStatus;

/**
 * 描述一次 Agent 工具调用的公开状态，不包含工具参数或返回正文。
 */
public record ToolExecutionEventPayload(
        /** AgentScope 为本次工具调用分配的标识。 */ String toolCallId,
        /** Blueprint 白名单中的稳定工具名称。 */ String toolName,
        /** 与 AgentScope 解耦的公开工具状态。 */ CreativeToolStatus status
) {
}
