package com.manga.dto;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.LocalDateTime;

/** SSE 传输的已提交助手事件。 */
public record AgentEventResponse(
        long sequenceNo,
        String eventType,
        JsonNode payload,
        LocalDateTime createdAt
) {
}
