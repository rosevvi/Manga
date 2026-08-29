package com.manga.common.security;

import java.util.List;

/** 表示由已验证 JWT 构造的当前认证主体。 */
public record AuthenticatedUser(
        Long userId,
        String username,
        String displayName,
        boolean guest,
        List<String> roles
) {
}
