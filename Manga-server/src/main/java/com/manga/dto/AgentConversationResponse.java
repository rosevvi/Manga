package com.manga.dto;

import java.time.LocalDateTime;

/** 助手会话摘要。 */
public record AgentConversationResponse(
        /** 会话唯一标识。 */
        String conversationId,
        /** 可选的绑定项目主键。 */
        Long projectId,
        /** 会话展示标题。 */
        String title,
        /** 会话当前状态。 */
        String status,
        /** 会话最后更新时间。 */
        LocalDateTime updatedAt
) {
}
