package com.manga.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

/** 无损审计归档副本，保留在线来源用于连续对话及审批追溯。 */
@Data
@TableName("manga_creative_run_archive")
public class CreativeRunArchive {
    /** 数据库主键。 */
    @TableId(type = IdType.AUTO)
    private Long id;
    /** 已终态且完成消息投影的 Run。 */
    private String runId;
    /** 归档格式版本。 */
    private Integer schemaVersion;
    /** 完整 Run、Step、事件、用量等审计副本；包含用户内容，管理员专用。 */
    private String payloadJson;
    /** 原始 UTF-8 payload 的完整性摘要。 */
    private String sha256;
    /** 创建时间。 */
    private LocalDateTime createdAt;
    /** 最后更新时间。 */
    private LocalDateTime updatedAt;
    /** 创建操作者。 */
    private String createdBy;
    /** 更新操作者。 */
    private String updatedBy;
}
