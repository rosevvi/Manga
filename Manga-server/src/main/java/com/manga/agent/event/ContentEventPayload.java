package com.manga.agent.event;

/**
 * 描述模型流式文本增量事件的稳定载荷。
 */
public record ContentEventPayload(
        /** 本次新增的文本片段。 */
        String delta
) {
}
