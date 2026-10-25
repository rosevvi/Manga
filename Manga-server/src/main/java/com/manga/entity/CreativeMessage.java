package com.manga.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.manga.agent.domain.CreativeMessageRole;
import com.manga.agent.domain.CreativeRunStatus;
import com.manga.agent.domain.CreativeToolStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

/** 持久化可从事件重建的用户、助手与工具消息。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("manga_creative_message")
public class CreativeMessage {
    /** 数据库主键。 */
    @TableId(type = IdType.AUTO)
    private Long id;
    /** 所属创作会话。 */
    private String conversationId;
    /** 来源运行。 */
    private String runId;
    /** 幂等消息投影键。 */
    private String projectionKey;
    /** 对用户展示的消息角色。 */
    private CreativeMessageRole role;
    /** 消息所属运行的展示状态。 */
    private CreativeRunStatus status;
    /** 事件投影得到的可见文本。 */
    private String content;
    /** 可选的工具调用标识。 */
    private String toolCallId;
    /** 可选的工具稳定名称。 */
    private String toolName;
    /** 可选的公开工具状态。 */
    private CreativeToolStatus toolStatus;
    /** 首次来源事件序号。 */
    private Long firstSequence;
    /** 最后已应用事件序号。 */
    private Long lastSequence;
    /** 记录创建时间。 */
    private LocalDateTime createdAt;
    /** 记录更新时间。 */
    private LocalDateTime updatedAt;
    /** 记录创建操作者。 */
    private String createdBy;
    /** 记录更新操作者。 */
    private String updatedBy;
}
