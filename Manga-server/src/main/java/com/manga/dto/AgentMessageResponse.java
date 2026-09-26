package com.manga.dto;

import java.time.LocalDateTime;

/** 助手历史消息。 */
public record AgentMessageResponse(
        /** 用户或助手消息角色。 */
        String role,
        /** 消息正文。 */
        String content,
        /** 会话内稳定排序序号。 */
        long messageOrder,
        /** 消息创建时间。 */
        LocalDateTime createdAt
) {
}
