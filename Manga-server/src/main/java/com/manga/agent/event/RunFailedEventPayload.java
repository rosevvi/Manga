package com.manga.agent.event;

/**
 * 描述 Run 失败事件的稳定载荷。
 */
public record RunFailedEventPayload(
        /** 稳定失败编码。 */
        String code,
        /** 脱敏后的失败摘要。 */
        String message
) {
}
