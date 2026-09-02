package com.manga.dto;

import com.manga.common.enums.StoryboardShotStatus;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import static com.manga.common.constant.ValidationConstants.*;

/** 承载分镜镜头的完整更新。 */
public record StoryboardShotUpdateRequest(
        /** 分镜标题。 */
        @NotBlank(message = SHOT_TITLE_REQUIRED)
        @Size(max = SHOT_TITLE_MAX_LENGTH, message = SHOT_TITLE_TOO_LONG)
        String title,
        /** 分镜场景名称。 */
        @Size(max = SHOT_SCENE_NAME_MAX_LENGTH, message = SHOT_SCENE_NAME_TOO_LONG)
        String sceneName,
        /** 景别或镜头类型。 */
        @Size(max = SHOT_TYPE_MAX_LENGTH, message = SHOT_TYPE_TOO_LONG)
        String shotType,
        /** 镜头运动方式。 */
        @Size(max = SHOT_CAMERA_MOVEMENT_MAX_LENGTH, message = SHOT_CAMERA_MOVEMENT_TOO_LONG)
        String cameraMovement,
        /** 镜头时长，单位为秒。 */
        @Min(value = 0, message = SHOT_DURATION_INVALID)
        @Max(value = SHOT_DURATION_MAX_SECONDS, message = SHOT_DURATION_INVALID)
        Integer durationSeconds,
        /** 分镜画面内容。 */
        @Size(max = SHOT_CONTENT_MAX_LENGTH, message = SHOT_CONTENT_TOO_LONG)
        String content,
        /** 分镜对白。 */
        @Size(max = SHOT_DIALOGUE_MAX_LENGTH, message = SHOT_DIALOGUE_TOO_LONG)
        String dialogue,
        /** 分镜音效说明。 */
        @Size(max = SHOT_SOUND_EFFECT_MAX_LENGTH, message = SHOT_SOUND_EFFECT_TOO_LONG)
        String soundEffect,
        /** 分镜参考图片地址。 */
        @Size(max = SHOT_IMAGE_URL_MAX_LENGTH, message = SHOT_IMAGE_URL_TOO_LONG)
        String imageUrl,
        /** 分镜制作备注。 */
        @Size(max = SHOT_NOTES_MAX_LENGTH, message = SHOT_NOTES_TOO_LONG)
        String notes,
        /** 分镜镜头状态。 */
        StoryboardShotStatus status
) {
}
