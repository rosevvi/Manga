package com.manga.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/** 为历史会话页面投影的用户和助手消息。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("manga_agent_message")
public class AgentMessage {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String conversationId;
    private String runId;
    private String role;
    private String content;
    private Long messageOrder;
    private LocalDateTime createdAt;
}
