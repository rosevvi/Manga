package com.manga.common.security;

import com.manga.common.constant.ExceptionMessageConstants;
import com.manga.common.constant.SecurityConstants;
import com.manga.common.enums.CommonResponseCode;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collection;
import java.util.List;

/** 从标准 Bearer 请求头验证 JWT 并建立当前请求的安全上下文。 */
@Component
@Slf4j
@RequiredArgsConstructor
public class TokenAuthenticationFilter extends OncePerRequestFilter {

    private final JwtDecoder jwtDecoder;
    private final SecurityErrorResponseWriter errorResponseWriter;

    /** 解析 Bearer Token 并建立认证上下文。 */
    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (!StringUtils.hasText(authorization)) {
            filterChain.doFilter(request, response);
            return;
        }
        if (!authorization.startsWith(SecurityConstants.BEARER_PREFIX)
                || authorization.length() == SecurityConstants.BEARER_PREFIX.length()) {
            reject(request, response, "unsupported authorization header");
            return;
        }

        try {
            Jwt jwt = jwtDecoder.decode(authorization.substring(SecurityConstants.BEARER_PREFIX.length()));
            AuthenticatedUser principal = toPrincipal(jwt);
            List<SimpleGrantedAuthority> authorities = principal.roles().stream()
                    .map(role -> new SimpleGrantedAuthority(SecurityConstants.AUTHORITY_PREFIX + role))
                    .toList();
            UsernamePasswordAuthenticationToken authentication =
                    UsernamePasswordAuthenticationToken.authenticated(principal, null, authorities);
            authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
            SecurityContextHolder.getContext().setAuthentication(authentication);
        } catch (JwtException | IllegalArgumentException exception) {
            SecurityContextHolder.clearContext();
            reject(request, response, exception.getClass().getSimpleName());
            return;
        }
        filterChain.doFilter(request, response);
    }

    /** 将 JWT 声明转换为认证用户。 */
    private AuthenticatedUser toPrincipal(Jwt jwt) {
        if (!StringUtils.hasText(jwt.getSubject())) {
            throw new IllegalArgumentException(ExceptionMessageConstants.JWT_SUBJECT_REQUIRED);
        }
        Object rawUserId = jwt.getClaim(SecurityConstants.USER_ID_CLAIM);
        Long userId = rawUserId instanceof Number number ? number.longValue() : null;
        Object rawRoles = jwt.getClaim(SecurityConstants.ROLES_CLAIM);
        List<String> roles = rawRoles instanceof Collection<?> values
                ? values.stream().filter(String.class::isInstance).map(String.class::cast).distinct().sorted().toList()
                : List.of();
        return new AuthenticatedUser(
                userId,
                jwt.getSubject(),
                jwt.getClaimAsString(SecurityConstants.DISPLAY_NAME_CLAIM),
                Boolean.TRUE.equals(jwt.getClaimAsBoolean(SecurityConstants.GUEST_CLAIM)),
                roles);
    }

    /** 返回统一的未认证响应。 */
    private void reject(HttpServletRequest request, HttpServletResponse response, String reason) throws IOException {
        log.warn("Token authentication rejected path={} reason={}", request.getRequestURI(), reason);
        errorResponseWriter.write(response, HttpServletResponse.SC_UNAUTHORIZED, CommonResponseCode.UNAUTHORIZED);
    }
}
