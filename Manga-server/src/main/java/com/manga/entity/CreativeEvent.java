package com.manga.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.manga.agent.domain.CreativeEventType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 持久化 Creative Run 的追加式事件日志。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("manga_creative_event")
public class CreativeEvent {

    /** 数据库主键。 */
    @TableId(type = IdType.AUTO)
    private Long id;
    /** 所属 Run 标识。 */
    private String runId;
    /** 可选的所属 Step 标识。 */
    private String stepId;
    /** Run 内严格递增事件序号。 */
    private Long sequenceNo;
    /** 事件 Envelope Schema 版本。 */
    private Integer schemaVersion;
    /** 稳定事件类型。 */
    private CreativeEventType eventType;
    /** 产生事件的运行时组件或能力。 */
    private String source;
    /** 可选的工具调用、作业或审批关联标识。 */
    private String correlationId;
    /** 脱敏后的结构化事件载荷。 */
    private String payloadJson;
    /** 记录创建时间。 */
    private LocalDateTime createdAt;
    /** 记录最后更新时间。 */
    private LocalDateTime updatedAt;
    /** 记录创建操作者。 */
    private String createdBy;
    /** 记录最后更新操作者。 */
    private String updatedBy;
}
