package com.manga.common.constant;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;

import java.util.List;

/**
 * 统一维护认证和跨域相关的技术常量。
 */
public final class SecurityConstants {

    public static final String REQUESTED_WITH_HEADER = "X-Requested-With";
    public static final String TOKEN_TYPE = "Bearer";
    public static final String BEARER_PREFIX = TOKEN_TYPE + " ";
    public static final String AUTHORITY_PREFIX = "ROLE_";
    public static final String ROLES_CLAIM = "roles";
    public static final String USER_ID_CLAIM = "userId";
    public static final String DISPLAY_NAME_CLAIM = "displayName";
    public static final String GUEST_CLAIM = "guest";
    public static final String GUEST_USERNAME_PREFIX = "guest-";
    public static final String GUEST_DISPLAY_NAME_PREFIX = "Guest ";
    public static final int GUEST_ID_LENGTH = 8;
    public static final int JWT_MIN_SECRET_BYTES = 32;

    public static final List<String> CORS_ALLOWED_METHODS = List.of(
            HttpMethod.GET.name(),
            HttpMethod.POST.name(),
            HttpMethod.PUT.name(),
            HttpMethod.PATCH.name(),
            HttpMethod.DELETE.name(),
            HttpMethod.OPTIONS.name()
    );

    public static final List<String> CORS_ALLOWED_HEADERS = List.of(
            HttpHeaders.AUTHORIZATION,
            HttpHeaders.CONTENT_TYPE,
            REQUESTED_WITH_HEADER,
            LoggingConstants.TRACE_ID_HEADER
    );

    public static final List<String> CORS_EXPOSED_HEADERS = List.of(
            HttpHeaders.LOCATION,
            LoggingConstants.TRACE_ID_HEADER
    );

    private SecurityConstants() {
    }
}
