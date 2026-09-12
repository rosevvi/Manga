package com.manga.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/** 持久化用户助手会话。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("manga_agent_conversation")
public class AgentConversation {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String conversationId;
    private Long userId;
    private Long projectId;
    private String title;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
