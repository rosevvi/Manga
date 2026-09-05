package com.manga.dto;

import java.time.LocalDateTime;
import java.util.List;

/** 返回项目剧本原文和结构化内容。 */
public record ProjectScriptResponse(
        /** 剧本主键。 */
        Long id,
        /** 所属项目主键。 */
        Long projectId,
        /** 剧本标题。 */
        String title,
        /** 剧本简介。 */
        String synopsis,
        /** 剧本原始文本。 */
        String rawContent,
        /** 原始文本来源类型。 */
        String sourceType,
        /** 结构化解析状态。 */
        String parseStatus,
        /** 结构化版本。 */
        Integer structureVersion,
        /** 分集列表。 */
        List<ProjectScriptSaveRequest.Episode> episodes,
        /** 最近一次错误摘要。 */
        String lastError,
        /** 创建时间。 */
        LocalDateTime createdAt,
        /** 最后更新时间。 */
        LocalDateTime updatedAt
) {
}
