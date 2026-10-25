package com.manga.agent.api;

import java.time.LocalDateTime;
/** 会话列表中的轻量摘要。 */
public record CreativeConversationView(
        /** 数据库主键，用作列表分页游标。 */ long id,
        /** 对外会话标识。 */ String conversationId,
        /** 会话标题。 */ String title,
        /** 可选项目主键。 */ Long projectId,
        /** 当前状态隔离代次。 */ long stateGeneration,
        /** 最后更新时间。 */ LocalDateTime updatedAt
) { }
