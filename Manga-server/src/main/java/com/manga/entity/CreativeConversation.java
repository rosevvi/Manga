package com.manga.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.manga.agent.domain.CreativeConversationStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 持久化用户与创作系统之间的连续会话。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("manga_creative_conversation")
public class CreativeConversation {

    /** 数据库主键。 */
    @TableId(type = IdType.AUTO)
    private Long id;
    /** 对外暴露的会话唯一标识。 */
    private String conversationId;
    /** 会话所属用户主键。 */
    private Long userId;
    /** 可选的绑定项目主键。 */
    private Long projectId;
    /** 会话标题。 */
    private String title;
    /** 会话当前状态。 */
    private CreativeConversationStatus status;
    /** 记录创建时间。 */
    private LocalDateTime createdAt;
    /** 记录最后更新时间。 */
    private LocalDateTime updatedAt;
    /** 记录创建操作者。 */
    private String createdBy;
    /** 记录最后更新操作者。 */
    private String updatedBy;
}
