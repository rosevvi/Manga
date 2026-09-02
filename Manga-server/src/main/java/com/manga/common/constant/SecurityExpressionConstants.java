package com.manga.common.constant;

/**
 * 统一维护方法级鉴权表达式。
 */
public final class SecurityExpressionConstants {

    /** 管理员方法鉴权表达式。 */
    public static final String HAS_ADMIN_ROLE = "hasRole('ADMIN')";

    /** 禁止实例化常量类。 */
    private SecurityExpressionConstants() {
    }
}
