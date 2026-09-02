package com.manga.common.constant;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;

import java.util.List;

/**
 * 统一维护认证和跨域相关的技术常量。
 */
public final class SecurityConstants {

    /** Ajax 请求标识头名称。 */
    public static final String REQUESTED_WITH_HEADER = "X-Requested-With";
    /** 访问令牌类型。 */
    public static final String TOKEN_TYPE = "Bearer";
    /** Bearer 认证值前缀。 */
    public static final String BEARER_PREFIX = TOKEN_TYPE + " ";
    /** Spring Security 角色权限前缀。 */
    public static final String AUTHORITY_PREFIX = "ROLE_";
    /** JWT 角色声明名称。 */
    public static final String ROLES_CLAIM = "roles";
    /** JWT 用户主键声明名称。 */
    public static final String USER_ID_CLAIM = "userId";
    /** JWT 显示名称声明名称。 */
    public static final String DISPLAY_NAME_CLAIM = "displayName";
    /** JWT 游客标记声明名称。 */
    public static final String GUEST_CLAIM = "guest";
    /** 游客用户名固定前缀。 */
    public static final String GUEST_USERNAME_PREFIX = "guest-";
    /** 游客显示名称固定前缀。 */
    public static final String GUEST_DISPLAY_NAME_PREFIX = "Guest ";
    /** 游客随机标识长度。 */
    public static final int GUEST_ID_LENGTH = 8;
    /** JWT 签名密钥最小字节数。 */
    public static final int JWT_MIN_SECRET_BYTES = 32;

    /** 跨域允许的 HTTP 方法集合。 */
    public static final List<String> CORS_ALLOWED_METHODS = List.of(
            HttpMethod.GET.name(),
            HttpMethod.POST.name(),
            HttpMethod.PUT.name(),
            HttpMethod.PATCH.name(),
            HttpMethod.DELETE.name(),
            HttpMethod.OPTIONS.name()
    );

    /** 跨域允许的请求头集合。 */
    public static final List<String> CORS_ALLOWED_HEADERS = List.of(
            HttpHeaders.AUTHORIZATION,
            HttpHeaders.CONTENT_TYPE,
            REQUESTED_WITH_HEADER,
            LoggingConstants.TRACE_ID_HEADER
    );

    /** 跨域允许前端读取的响应头集合。 */
    public static final List<String> CORS_EXPOSED_HEADERS = List.of(
            HttpHeaders.LOCATION,
            LoggingConstants.TRACE_ID_HEADER
    );

    /** 禁止实例化常量类。 */
    private SecurityConstants() {
    }
}
