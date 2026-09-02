package com.manga.dto;

import com.manga.common.constant.ValidationConstants;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 接收当前用户修改密码时的新旧密码。
 */
public record ChangePasswordRequest(
        /** 当前密码。 */
        @NotBlank(message = ValidationConstants.PASSWORD_REQUIRED)
        @Size(max = ValidationConstants.PASSWORD_MAX_LENGTH, message = ValidationConstants.PASSWORD_TOO_LONG)
        String currentPassword,
        /** 新密码。 */
        @NotBlank(message = ValidationConstants.PASSWORD_REQUIRED)
        @Size(
                min = ValidationConstants.PASSWORD_MIN_LENGTH,
                max = ValidationConstants.PASSWORD_MAX_LENGTH,
                message = ValidationConstants.NEW_PASSWORD_TOO_SHORT
        )
        String newPassword
) {
}
