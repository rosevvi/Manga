package com.manga.config;

import com.manga.common.constant.SecurityConstants;
import com.manga.common.enums.CommonResponseCode;
import com.manga.common.security.SecurityErrorResponseWriter;
import com.manga.common.security.TokenAuthenticationFilter;
import com.manga.config.properties.MangaSecurityProperties;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/** 配置无状态 JWT 鉴权、密码编码和跨域访问规则。 */
@Configuration
@EnableMethodSecurity
@Slf4j
@RequiredArgsConstructor
public class SecurityConfig {

    private final MangaSecurityProperties securityProperties;
    private final TokenAuthenticationFilter tokenAuthenticationFilter;
    private final SecurityErrorResponseWriter errorResponseWriter;

    /** 定义公开接口以及其他接口的 Bearer Token 访问策略。 */
    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers(
                                "/api/v1/public/**",
                                "/api/v1/auth/login",
                                "/api/v1/auth/guest",
                                "/api/v1/auth/wechat/**",
                                "/uploads/**",
                                "/actuator/health",
                                "/actuator/info",
                                "/error"
                        )
                        .permitAll()
                        .anyRequest().authenticated())
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint((request, response, exception) -> {
                            log.warn("Request authentication rejected path={}", request.getRequestURI());
                            errorResponseWriter.write(
                                    response, HttpServletResponse.SC_UNAUTHORIZED, CommonResponseCode.UNAUTHORIZED);
                        })
                        .accessDeniedHandler((request, response, exception) -> {
                            log.warn("Request authorization rejected path={}", request.getRequestURI());
                            errorResponseWriter.write(
                                    response, HttpServletResponse.SC_FORBIDDEN, CommonResponseCode.FORBIDDEN);
                        }))
                .addFilterBefore(tokenAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .build();
    }

    /** 提供统一的 BCrypt 密码编码器。 */
    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /** 根据外部配置创建前后端跨域策略。 */
    @Bean
    CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(securityProperties.allowedOrigins());
        configuration.setAllowedMethods(SecurityConstants.CORS_ALLOWED_METHODS);
        configuration.setAllowedHeaders(SecurityConstants.CORS_ALLOWED_HEADERS);
        configuration.setExposedHeaders(SecurityConstants.CORS_EXPOSED_HEADERS);
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(securityProperties.corsMaxAge().toSeconds());

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

}
