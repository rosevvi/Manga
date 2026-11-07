package com.manga.dto;

import com.manga.common.enums.ScriptChapterParseStatus;

import java.time.LocalDateTime;
import java.util.List;

/** 返回单个章节的正文和结构化内容。 */
public record ProjectScriptChapterResponse(
        /** 章节主键。 */ Long id,
        /** 所属剧本。 */ Long scriptId,
        /** 展示章节序号。 */ int chapterNumber,
        /** 标题。 */ String title,
        /** 简介。 */ String synopsis,
        /** 完整原文。 */ String rawContent,
        /** 内容来源。 */ String sourceType,
        /** 结构化处理状态。 */ ScriptChapterParseStatus parseStatus,
        /** 场景结构版本。 */ Integer structureVersion,
        /** 场景与对白。 */ List<ProjectScriptSaveRequest.Scene> scenes,
        /** 处理失败摘要。 */ String lastError,
        /** 稳定排序。 */ int sortOrder,
        /** 最后修改时间。 */ LocalDateTime updatedAt,
        /** 人工保存必须回传的业务版本。 */ Long revision
) {
}
