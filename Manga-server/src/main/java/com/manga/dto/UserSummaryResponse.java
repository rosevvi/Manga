package com.manga.dto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 返回管理端所需的用户公开摘要。
 */
public record UserSummaryResponse(
        Long id,
        String username,
        String displayName,
        String status,
        String registrationSource,
        List<String> roles,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        String createdBy,
        String updatedBy
) {
}
