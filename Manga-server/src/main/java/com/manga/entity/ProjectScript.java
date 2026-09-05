package com.manga.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/** 保存项目剧本原文和结构化结果。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("manga_project_script")
public class ProjectScript {

    /** 剧本主键。 */
    @TableId(type = IdType.AUTO)
    private Long id;
    /** 所属项目主键。 */
    private Long projectId;
    /** 剧本标题。 */
    private String title;
    /** 剧本简介。 */
    private String synopsis;
    /** 用户导入或编辑的原始文本。 */
    private String rawContent;
    /** 原始文本来源类型。 */
    private String sourceType;
    /** 结构化解析状态。 */
    private String parseStatus;
    /** 结构化数据版本。 */
    private Integer structureVersion;
    /** 结构化剧本 JSON。 */
    private String structureJson;
    /** 最近一次解析错误摘要。 */
    private String lastError;
    /** 创建时间。 */
    private LocalDateTime createdAt;
    /** 最后更新时间。 */
    private LocalDateTime updatedAt;
    /** 创建人标识。 */
    private String createdBy;
    /** 最后更新人标识。 */
    private String updatedBy;
}
