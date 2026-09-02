package com.manga.dto;

import com.manga.common.constant.ValidationConstants;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 接收账号密码登录参数。
 */
public record LoginRequest(
        /** 用户名。 */
        @NotBlank(message = ValidationConstants.USERNAME_REQUIRED)
        @Size(max = ValidationConstants.USERNAME_MAX_LENGTH, message = ValidationConstants.USERNAME_TOO_LONG)
        String username,
        /** 密码明文，仅在当前请求或初始化过程中使用。 */
        @NotBlank(message = ValidationConstants.PASSWORD_REQUIRED)
        @Size(max = ValidationConstants.PASSWORD_MAX_LENGTH, message = ValidationConstants.PASSWORD_TOO_LONG)
        String password
) {
}
