package com.manga.dto;

import com.manga.common.enums.StoryboardShotStatus;

import java.time.LocalDateTime;

/** 返回分镜镜头的完整创作信息。 */
public record StoryboardShotResponse(
        /** 分镜镜头主键。 */
        Long id,
        /** 所属项目主键。 */
        Long projectId,
        Long chapterId,
        /** 分镜排序值。 */
        int sortOrder,
        /** 分镜镜头编号。 */
        String shotNumber,
        /** 分镜标题。 */
        String title,
        /** 分镜场景名称。 */
        String sceneName,
        /** 景别或镜头类型。 */
        String shotType,
        /** 镜头运动方式。 */
        String cameraMovement,
        /** 镜头时长，单位为秒。 */
        int durationSeconds,
        /** 分镜画面内容。 */
        String content,
        /** 分镜对白。 */
        String dialogue,
        /** 分镜音效说明。 */
        String soundEffect,
        /** 分镜参考图片地址。 */
        String imageUrl,
        /** 分镜制作备注。 */
        String notes,
        /** 分镜镜头状态。 */
        StoryboardShotStatus status,
        /** 创建时间。 */
        LocalDateTime createdAt,
        /** 最后更新时间。 */
        LocalDateTime updatedAt
) {
}
