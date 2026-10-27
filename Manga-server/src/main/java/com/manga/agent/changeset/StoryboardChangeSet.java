package com.manga.agent.changeset;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.util.List;

import static com.manga.common.constant.ValidationConstants.SHOT_CAMERA_MOVEMENT_MAX_LENGTH;
import static com.manga.common.constant.ValidationConstants.SHOT_CONTENT_MAX_LENGTH;
import static com.manga.common.constant.ValidationConstants.SHOT_DIALOGUE_MAX_LENGTH;
import static com.manga.common.constant.ValidationConstants.SHOT_DURATION_MAX_SECONDS;
import static com.manga.common.constant.ValidationConstants.SHOT_SOUND_EFFECT_MAX_LENGTH;
import static com.manga.common.constant.ValidationConstants.SHOT_TYPE_MAX_LENGTH;

/** Schema v1：向一个明确章节追加有限数量的分镜草稿，不生成付费媒体。 */
public record StoryboardChangeSet(
        /** 必须属于当前项目的章节主键。 */ @NotNull @Positive Long chapterId,
        /** 本次原子追加的草稿镜头。 */ @NotEmpty @Size(max = 100) List<@NotNull @Valid Shot> shots
) {
    /** 允许模型提出的镜头字段，不接受状态、图片地址或项目归属。 */
    public record Shot(
            /** 镜头标题。 */ @NotBlank @Size(max = 120) String title,
            /** 场景名称。 */ @Size(max = 120) String sceneName,
            /** 景别。 */ @Size(max = SHOT_TYPE_MAX_LENGTH) String shotType,
            /** 运镜说明。 */ @Size(max = SHOT_CAMERA_MOVEMENT_MAX_LENGTH) String cameraMovement,
            /** 建议时长，单位秒。 */ @Min(0) @Max(SHOT_DURATION_MAX_SECONDS) Integer durationSeconds,
            /** 画面描述。 */ @Size(max = SHOT_CONTENT_MAX_LENGTH) String content,
            /** 对白。 */ @Size(max = SHOT_DIALOGUE_MAX_LENGTH) String dialogue,
            /** 音效。 */ @Size(max = SHOT_SOUND_EFFECT_MAX_LENGTH) String soundEffect,
            /** 制作备注。 */ @Size(max = 2000) String notes
    ) { }
}
