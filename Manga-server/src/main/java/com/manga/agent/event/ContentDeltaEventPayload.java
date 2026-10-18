package com.manga.agent.event;

/**
 * 定义可供前端增量拼接的文本事件载荷。
 */
public record ContentDeltaEventPayload(
        /** 本次新增的文本片段。 */
        String delta
) {
}
