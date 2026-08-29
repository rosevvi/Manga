package com.manga.common.security;

import com.manga.common.enums.CommonResponseCode;
import com.manga.common.exception.BusinessException;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/** 统一从 Spring Security 上下文读取当前认证用户。 */
public final class SecurityUtils {

    /** 返回已认证的当前用户；请求未认证时返回 {@code null}。 */
    public static AuthenticatedUser getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return null;
        }
        return authentication.getPrincipal() instanceof AuthenticatedUser user ? user : null;
    }

    /** 返回已认证的当前用户；请求未认证时抛出统一业务异常。 */
    public static AuthenticatedUser requireCurrentUser() {
        AuthenticatedUser user = getCurrentUser();
        if (user == null) {
            throw new BusinessException(CommonResponseCode.UNAUTHORIZED, HttpStatus.UNAUTHORIZED);
        }
        return user;
    }

    /** 返回当前用户名；请求未认证时返回 {@code null}。 */
    public static String getCurrentUsername() {
        AuthenticatedUser user = getCurrentUser();
        return user == null ? null : user.username();
    }

    /** 返回当前用户名；请求未认证时抛出统一业务异常。 */
    public static String requireCurrentUsername() {
        return requireCurrentUser().username();
    }

    /** 判断当前认证主体是否为游客。 */
    public static boolean isCurrentUserGuest() {
        AuthenticatedUser user = getCurrentUser();
        return user != null && user.guest();
    }

    /** 返回正式用户主键，并拒绝游客和缺少用户主键的令牌。 */
    public static long requireCurrentUserId() {
        AuthenticatedUser user = requireCurrentUser();
        if (user.guest() || user.userId() == null) {
            throw new BusinessException(CommonResponseCode.FORBIDDEN, HttpStatus.FORBIDDEN);
        }
        return user.userId();
    }

    private SecurityUtils() {
    }
}
