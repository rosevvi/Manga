package com.manga.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/** Agent 运行事件的追加日志记录。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("manga_agent_event")
public class AgentEvent {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String runId;
    private Long sequenceNo;
    private String eventType;
    private String payloadJson;
    private LocalDateTime createdAt;
}
