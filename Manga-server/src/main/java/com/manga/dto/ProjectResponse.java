package com.manga.dto;

import com.manga.common.enums.ProjectStatus;

import java.time.LocalDateTime;

/** 返回项目详情及其分镜数量摘要。 */
public record ProjectResponse(
        Long id,
        String name,
        String description,
        String coverUrl,
        String genre,
        ProjectStatus status,
        int shotCount,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
