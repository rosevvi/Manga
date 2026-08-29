package com.manga.common.constant;

/**
 * 统一维护方法级鉴权表达式。
 */
public final class SecurityExpressionConstants {

    public static final String HAS_ADMIN_ROLE = "hasRole('ADMIN')";

    private SecurityExpressionConstants() {
    }
}
