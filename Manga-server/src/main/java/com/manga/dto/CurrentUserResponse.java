package com.manga.dto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 返回当前认证主体的基础资料和角色列表。
 */
public record CurrentUserResponse(
        Long id,
        String username,
        String displayName,
        boolean guest,
        List<String> roles,
        String status,
        String registrationSource,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
