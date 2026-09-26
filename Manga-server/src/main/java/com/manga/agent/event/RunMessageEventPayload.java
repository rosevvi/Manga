package com.manga.agent.event;

/**
 * 描述携带安全用户提示的 Run 事件载荷。
 */
public record RunMessageEventPayload(
        /** 面向用户展示的安全提示。 */
        String message
) {
}
