package com.manga.dto;

import java.time.LocalDateTime;

/** 助手历史消息。 */
public record AgentMessageResponse(
        String role,
        String content,
        long messageOrder,
        LocalDateTime createdAt
) {
}
