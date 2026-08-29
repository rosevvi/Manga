package com.manga.controller;

import com.manga.common.api.ApiResponse;
import com.manga.dto.AuthResponse;
import com.manga.dto.LoginRequest;
import com.manga.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 提供账号密码与游客两种登录接口。
 */
@RestController
@RequestMapping("/api/v1/auth")
@Slf4j
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    /**
     * 校验账号密码并返回访问令牌。
     */
    @PostMapping("/login")
    public ApiResponse<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        log.info("[AuthController#login] request username={}", request.username());
        AuthResponse result = authService.login(request);
        log.info(
                "[AuthController#login] response userId={} guest={} roles={} expiresInSeconds={}",
                result.user().id(),
                result.user().guest(),
                result.user().roles(),
                result.expiresIn()
        );
        return ApiResponse.success(result);
    }

    /**
     * 无需账号即可创建短期游客身份。
     */
    @PostMapping("/guest")
    public ApiResponse<AuthResponse> loginAsGuest() {
        log.info("[AuthController#loginAsGuest] request");
        AuthResponse result = authService.loginAsGuest();
        log.info(
                "[AuthController#loginAsGuest] response guest={} roles={} expiresInSeconds={}",
                result.user().guest(),
                result.user().roles(),
                result.expiresIn()
        );
        return ApiResponse.success(result);
    }
}
