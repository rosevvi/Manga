package com.manga.dto;

import com.manga.common.enums.ScriptChapterParseStatus;

import java.time.LocalDateTime;
import java.util.List;

/** 返回单个章节的正文和结构化内容。 */
public record ProjectScriptChapterResponse(
        Long id,
        Long scriptId,
        int chapterNumber,
        String title,
        String synopsis,
        String rawContent,
        String sourceType,
        ScriptChapterParseStatus parseStatus,
        Integer structureVersion,
        List<ProjectScriptSaveRequest.Scene> scenes,
        String lastError,
        int sortOrder,
        LocalDateTime updatedAt
) {
}
