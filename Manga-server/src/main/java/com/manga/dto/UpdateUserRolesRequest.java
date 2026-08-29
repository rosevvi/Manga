package com.manga.dto;

import com.manga.common.constant.ValidationConstants;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;

import java.util.Set;

/**
 * 接收管理员替换用户角色的请求参数。
 */
public record UpdateUserRolesRequest(
        @NotEmpty(message = ValidationConstants.ROLE_REQUIRED)
        Set<@Pattern(
                regexp = ValidationConstants.ROLE_CODE_PATTERN,
                message = ValidationConstants.ROLE_FORMAT_INVALID
        ) String> roleCodes
) {
}
