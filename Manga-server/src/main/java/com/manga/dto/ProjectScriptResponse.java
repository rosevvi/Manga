package com.manga.dto;

import java.time.LocalDateTime;
import java.util.List;

/** 返回剧本元数据和章节轻量列表，不包含任何整本正文。 */
public record ProjectScriptResponse(
        Long id,
        Long projectId,
        String title,
        String synopsis,
        String sourceType,
        String parseStatus,
        Integer structureVersion,
        int chapterCount,
        List<ProjectScriptChapterSummaryResponse> chapters,
        String lastError,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
