package com.manga.dto;

import com.manga.common.enums.TaskStatus;
import com.manga.common.enums.TaskType;

import java.time.LocalDateTime;

/** 返回后台任务的可恢复状态摘要。 */
public record TaskResponse(
        Long id,
        Long projectId,
        TaskType taskType,
        TaskStatus status,
        String title,
        int totalUnits,
        int completedUnits,
        int failedUnits,
        String currentUnit,
        String lastError,
        LocalDateTime createdAt,
        LocalDateTime startedAt,
        LocalDateTime finishedAt
) {
}
