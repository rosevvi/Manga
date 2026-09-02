package com.manga.common.security;

import java.util.List;

/** 表示由已验证 JWT 构造的当前认证主体。 */
public record AuthenticatedUser(
        /** 关联用户主键。 */
        Long userId,
        /** 用户名。 */
        String username,
        /** 用户显示名称。 */
        String displayName,
        /** 当前身份是否为游客。 */
        boolean guest,
        /** 当前用户角色集合。 */
        List<String> roles
) {
}
