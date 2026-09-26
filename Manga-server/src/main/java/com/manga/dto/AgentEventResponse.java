package com.manga.dto;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.LocalDateTime;

/** SSE 传输的已提交助手事件。 */
public record AgentEventResponse(
        /** Run 内严格递增的事件序号。 */
        long sequenceNo,
        /** 稳定事件类型。 */
        String eventType,
        /** 与事件类型匹配的结构化载荷。 */
        JsonNode payload,
        /** 事件提交时间。 */
        LocalDateTime createdAt
) {
}
