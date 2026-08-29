package com.manga.dto;

import com.manga.common.enums.StoryboardShotStatus;

import java.time.LocalDateTime;

/** 返回分镜镜头的完整创作信息。 */
public record StoryboardShotResponse(
        Long id,
        Long projectId,
        int sortOrder,
        String shotNumber,
        String title,
        String sceneName,
        String shotType,
        String cameraMovement,
        int durationSeconds,
        String content,
        String dialogue,
        String soundEffect,
        String imageUrl,
        String notes,
        StoryboardShotStatus status,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
