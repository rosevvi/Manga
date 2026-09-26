package com.manga.agent.event;

/**
 * 描述工具调用状态事件的稳定载荷。
 */
public record ToolCallEventPayload(
        /** 本次调用的稳定工具名称。 */
        String toolName
) {
}
