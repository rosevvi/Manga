package com.manga.dto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 返回当前认证主体的基础资料和角色列表。
 */
public record CurrentUserResponse(
        /** 当前用户主键。 */
        Long id,
        /** 用户名。 */
        String username,
        /** 用户显示名称。 */
        String displayName,
        /** 当前身份是否为游客。 */
        boolean guest,
        /** 当前用户角色集合。 */
        List<String> roles,
        /** 当前用户状态。 */
        String status,
        /** 账号注册来源。 */
        String registrationSource,
        /** 创建时间。 */
        LocalDateTime createdAt,
        /** 最后更新时间。 */
        LocalDateTime updatedAt
) {
}
