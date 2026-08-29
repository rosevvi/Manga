package com.manga.dto;

import java.time.LocalDateTime;

/**
 * 返回角色目录中的角色定义。
 */
public record RoleResponse(
        Long id,
        String code,
        String name,
        String description,
        boolean assignable,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        String createdBy,
        String updatedBy
) {
}
