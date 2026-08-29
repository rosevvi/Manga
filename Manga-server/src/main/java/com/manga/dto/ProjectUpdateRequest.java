package com.manga.dto;

import com.manga.common.enums.ProjectStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import static com.manga.common.constant.ValidationConstants.*;

/** 承载项目基础信息的完整更新。 */
public record ProjectUpdateRequest(
        @NotBlank(message = PROJECT_NAME_REQUIRED)
        @Size(max = PROJECT_NAME_MAX_LENGTH, message = PROJECT_NAME_TOO_LONG)
        String name,
        @Size(max = PROJECT_DESCRIPTION_MAX_LENGTH, message = PROJECT_DESCRIPTION_TOO_LONG)
        String description,
        @Size(max = PROJECT_COVER_URL_MAX_LENGTH, message = PROJECT_COVER_URL_TOO_LONG)
        String coverUrl,
        @Size(max = PROJECT_GENRE_MAX_LENGTH, message = PROJECT_GENRE_TOO_LONG)
        String genre,
        ProjectStatus status
) {
}
