package com.manga.dto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 返回管理端所需的用户公开摘要。
 */
public record UserSummaryResponse(
        /** 用户主键。 */
        Long id,
        /** 用户名。 */
        String username,
        /** 用户显示名称。 */
        String displayName,
        /** 用户状态。 */
        String status,
        /** 账号注册来源。 */
        String registrationSource,
        /** 当前用户角色集合。 */
        List<String> roles,
        /** 创建时间。 */
        LocalDateTime createdAt,
        /** 最后更新时间。 */
        LocalDateTime updatedAt,
        /** 创建人标识。 */
        String createdBy,
        /** 最后更新人标识。 */
        String updatedBy
) {
}
