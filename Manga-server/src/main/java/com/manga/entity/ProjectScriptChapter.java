package com.manga.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.manga.common.enums.ScriptChapterParseStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/** 保存项目单个章节的原文和结构化内容。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("manga_project_script_chapter")
public class ProjectScriptChapter {

    /** 章节主键。 */
    @TableId(type = IdType.AUTO)
    private Long id;
    /** 所属剧本主键。 */
    private Long scriptId;
    /** 章节标题。 */
    private String title;
    /** 章节简介。 */
    private String synopsis;
    /** 当前章节原文。 */
    private String rawContent;
    /** 原文长度摘要，不加载原文时使用。 */
    private Integer rawContentLength;
    /** 章节结构 JSON。 */
    private String structureJson;
    /** 内容来源。 */
    private String sourceType;
    /** 章节处理状态。 */
    private ScriptChapterParseStatus parseStatus;
    /** 结构版本。 */
    private Integer structureVersion;
    /** 最近一次错误摘要。 */
    private String lastError;
    /** 稳定排序值。 */
    private Integer sortOrder;
    /** 创建时间。 */
    private LocalDateTime createdAt;
    /** 更新时间。 */
    private LocalDateTime updatedAt;
    /** 创建人标识。 */
    private String createdBy;
    /** 更新人标识。 */
    private String updatedBy;
}
