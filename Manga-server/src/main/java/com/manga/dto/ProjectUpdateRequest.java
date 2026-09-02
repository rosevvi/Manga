package com.manga.dto;

import com.manga.common.enums.ProjectStatus;
import com.manga.common.enums.ProjectVisibilityScope;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import static com.manga.common.constant.ValidationConstants.*;

/** 承载项目基础信息的完整更新。 */
public record ProjectUpdateRequest(
        /** 项目名称。 */
        @NotBlank(message = PROJECT_NAME_REQUIRED)
        @Size(max = PROJECT_NAME_MAX_LENGTH, message = PROJECT_NAME_TOO_LONG)
        String name,
        /** 项目描述。 */
        @Size(max = PROJECT_DESCRIPTION_MAX_LENGTH, message = PROJECT_DESCRIPTION_TOO_LONG)
        String description,
        /** 项目封面图片地址。 */
        @Size(max = PROJECT_COVER_URL_MAX_LENGTH, message = PROJECT_COVER_URL_TOO_LONG)
        String coverUrl,
        /** 项目作品类型。 */
        @Size(max = PROJECT_GENRE_MAX_LENGTH, message = PROJECT_GENRE_TOO_LONG)
        String genre,
        /** 默认画面比例。 */
        @Size(max = PROJECT_ASPECT_RATIO_MAX_LENGTH, message = PROJECT_ASPECT_RATIO_TOO_LONG)
        String aspectRatio,
        /** 项目可见范围。 */
        ProjectVisibilityScope visibilityScope,
        /** 项目状态。 */
        ProjectStatus status,
        /** 画风预设标识或 custom。 */
        @Size(max = PROJECT_ART_STYLE_MAX_LENGTH, message = PROJECT_ART_STYLE_TOO_LONG)
        String artStyle,
        /** 自定义画风中文描述。 */
        @Size(max = PROJECT_ART_STYLE_DESCRIPTION_MAX_LENGTH, message = PROJECT_ART_STYLE_DESCRIPTION_TOO_LONG)
        String artStyleDescription,
        /** 自定义画风图片生成提示词。 */
        @Size(max = PROJECT_ART_STYLE_IMAGE_PROMPT_MAX_LENGTH, message = PROJECT_ART_STYLE_IMAGE_PROMPT_TOO_LONG)
        String artStyleImagePrompt,
        /** 画风参考图片地址。 */
        @Size(max = PROJECT_ART_STYLE_IMAGE_URL_MAX_LENGTH, message = PROJECT_ART_STYLE_IMAGE_URL_TOO_LONG)
        String artStyleImageUrl
) {
}
