package com.manga.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/** 可靠唤醒 SSE 订阅者的事件投递记录。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("manga_agent_event_outbox")
public class AgentEventOutbox {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String runId;
    private Long sequenceNo;
    private String status;
    private LocalDateTime publishedAt;
    private Integer attempts;
    private String lastError;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
