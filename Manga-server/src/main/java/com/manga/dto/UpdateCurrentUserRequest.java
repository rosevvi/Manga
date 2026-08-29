package com.manga.dto;

import com.manga.common.constant.ValidationConstants;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 接收当前用户可自行维护的基础资料。
 */
public record UpdateCurrentUserRequest(
        @NotBlank(message = ValidationConstants.DISPLAY_NAME_REQUIRED)
        @Size(
                min = ValidationConstants.DISPLAY_NAME_MIN_LENGTH,
                max = ValidationConstants.DISPLAY_NAME_MAX_LENGTH,
                message = ValidationConstants.DISPLAY_NAME_LENGTH_INVALID
        )
        String displayName
) {
}
