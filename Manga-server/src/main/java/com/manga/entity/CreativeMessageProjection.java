package com.manga.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

/** 消息批次的独立锁与领取信息，避免投影全程占用 Run 行锁。 */
@Data
@TableName("manga_creative_message_projection")
public class CreativeMessageProjection {
    /** 数据库主键。 */ @TableId(type = IdType.AUTO) private Long id;
    /** 唯一运行标识。 */ private String runId;
    /** 消息覆盖的已提交事件序号。 */ private Long projectedThroughSequence;
    /** 本批次领取者。 */ private String ownerId;
    /** 本批次租约截止时间。 */ private LocalDateTime leaseUntil;
    /** 创建时间。 */ private LocalDateTime createdAt;
    /** 更新时间。 */ private LocalDateTime updatedAt;
    /** 创建操作者。 */ private String createdBy;
    /** 最近修改操作者。 */ private String updatedBy;
}
