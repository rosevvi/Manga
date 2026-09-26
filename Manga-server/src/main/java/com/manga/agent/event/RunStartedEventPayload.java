package com.manga.agent.event;

/**
 * 描述 Run 启动事件的稳定载荷。
 */
public record RunStartedEventPayload(
        /** Run 所属的助手会话标识。 */
        String conversationId
) {
}
