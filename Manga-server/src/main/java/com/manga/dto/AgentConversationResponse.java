package com.manga.dto;

import java.time.LocalDateTime;

/** 助手会话摘要。 */
public record AgentConversationResponse(
        String conversationId,
        Long projectId,
        String title,
        String status,
        LocalDateTime updatedAt
) {
}
