package com.manga.dto;

import com.manga.common.enums.ScriptChapterParseStatus;

import java.time.LocalDateTime;

/** 返回单章节轻量摘要，不包含章节正文。 */
public record ProjectScriptChapterSummaryResponse(
        Long id,
        int chapterNumber,
        String title,
        String synopsis,
        ScriptChapterParseStatus parseStatus,
        int rawContentLength,
        int sceneCount,
        int sortOrder,
        LocalDateTime updatedAt
) {
}
