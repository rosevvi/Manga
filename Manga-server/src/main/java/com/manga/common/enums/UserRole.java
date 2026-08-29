package com.manga.common.enums;

import com.manga.common.constant.SecurityConstants;

/**
 * 定义平台支持的基础用户角色。
 */
public enum UserRole {
    GUEST("游客", "临时访问平台的游客角色", false),
    USER("用户", "平台普通用户角色", true),
    ADMIN("管理员", "负责平台用户和角色管理", true);

    private final String displayName;
    private final String description;
    private final boolean assignable;

    UserRole(String displayName, String description, boolean assignable) {
        this.displayName = displayName;
        this.description = description;
        this.assignable = assignable;
    }

    /**
     * 返回 Spring Security 使用的完整权限名称。
     */
    public String authority() {
        return SecurityConstants.AUTHORITY_PREFIX + name();
    }

    public String displayName() {
        return displayName;
    }

    public String description() {
        return description;
    }

    public boolean assignable() {
        return assignable;
    }
}
