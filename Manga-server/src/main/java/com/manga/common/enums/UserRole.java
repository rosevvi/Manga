package com.manga.common.enums;

import com.manga.common.constant.SecurityConstants;

/**
 * 定义平台支持的基础用户角色。
 */
public enum UserRole {
    /** 临时访问平台的游客角色。 */
    GUEST("游客", "临时访问平台的游客角色", false),
    /** 平台普通用户角色。 */
    USER("用户", "平台普通用户角色", true),
    /** 平台管理员角色。 */
    ADMIN("管理员", "负责平台用户和角色管理", true);

    /** 用户显示名称。 */
    private final String displayName;
    /** 用户角色描述。 */
    private final String description;
    /** 角色是否允许管理员分配。 */
    private final boolean assignable;

    /** 初始化枚举项元数据。 */
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

    /** 返回角色显示名称。 */
    public String displayName() {
        return displayName;
    }

    /** 返回角色说明。 */
    public String description() {
        return description;
    }

    /** 判断角色是否允许管理员分配。 */
    public boolean assignable() {
        return assignable;
    }
}
