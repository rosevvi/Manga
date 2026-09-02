package com.manga.dto;

import jakarta.validation.constraints.NotEmpty;

import java.util.List;

import static com.manga.common.constant.ValidationConstants.SHOT_ORDER_REQUIRED;

/** 承载项目内全部分镜镜头的新顺序。 */
public record StoryboardShotOrderRequest(
        /** 按目标顺序排列的分镜主键列表。 */
        @NotEmpty(message = SHOT_ORDER_REQUIRED)
        List<Long> shotIds
) {
}
