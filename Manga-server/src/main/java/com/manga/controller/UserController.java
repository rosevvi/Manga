package com.manga.controller;

import com.manga.common.api.ApiResponse;
import com.manga.common.constant.SecurityExpressionConstants;
import com.manga.common.security.SecurityUtils;
import com.manga.dto.CurrentUserResponse;
import com.manga.dto.ChangePasswordRequest;
import com.manga.dto.UpdateCurrentUserRequest;
import com.manga.dto.UpdateUserRolesRequest;
import com.manga.dto.UserSummaryResponse;
import com.manga.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 提供当前身份读取和管理员用户管理接口。
 */
@RestController
@RequestMapping("/api/v1/users")
@Slf4j
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    /**
     * 返回当前令牌中的用户或游客身份。
     */
    @GetMapping("/me")
    public ApiResponse<CurrentUserResponse> currentUser() {
        log.info("[UserController#currentUser] request subject={}", SecurityUtils.getCurrentUsername());
        CurrentUserResponse result = userService.getCurrentUser();
        log.info("[UserController#currentUser] response userId={} guest={} roles={}",
                result.id(), result.guest(), result.roles());
        return ApiResponse.success(result);
    }

    /**
     * 更新当前数据库用户可自行维护的基础资料。
     */
    @PutMapping("/me")
    public ApiResponse<CurrentUserResponse> updateCurrentUser(
            @Valid @RequestBody UpdateCurrentUserRequest request) {
        log.info(
                "[UserController#updateCurrentUser] request subject={} displayName={}",
                SecurityUtils.getCurrentUsername(),
                request.displayName()
        );
        CurrentUserResponse result = userService.updateCurrentUser(request);
        log.info(
                "[UserController#updateCurrentUser] response userId={} displayName={} roles={}",
                result.id(),
                result.displayName(),
                result.roles()
        );
        return ApiResponse.success(result);
    }

    /**
     * 校验当前密码后修改登录密码。
     */
    @PutMapping("/me/password")
    public ApiResponse<Void> changePassword(
            @Valid @RequestBody ChangePasswordRequest request) {
        String subject = SecurityUtils.getCurrentUsername();
        log.info("[UserController#changePassword] request subject={}", subject);
        userService.changePassword(request);
        log.info("[UserController#changePassword] response subject={} success={}", subject, true);
        return ApiResponse.success(null);
    }

    /**
     * 仅允许管理员查看数据库用户列表。
     */
    @GetMapping
    @PreAuthorize(SecurityExpressionConstants.HAS_ADMIN_ROLE)
    public ApiResponse<List<UserSummaryResponse>> findAll() {
        log.info("[UserController#findAll] request subject={}", SecurityUtils.getCurrentUsername());
        List<UserSummaryResponse> result = userService.findAll();
        log.info("[UserController#findAll] response count={}", result.size());
        return ApiResponse.success(result);
    }

    /**
     * 仅允许管理员替换指定用户的角色集合。
     */
    @PutMapping("/{userId}/roles")
    @PreAuthorize(SecurityExpressionConstants.HAS_ADMIN_ROLE)
    public ApiResponse<UserSummaryResponse> replaceRoles(
            @PathVariable long userId,
            @Valid @RequestBody UpdateUserRolesRequest request) {
        log.info(
                "[UserController#replaceRoles] request userId={} actor={} roles={}",
                userId,
                SecurityUtils.getCurrentUsername(),
                request.roleCodes()
        );
        UserSummaryResponse result = userService.replaceRoles(userId, request);
        log.info(
                "[UserController#replaceRoles] response userId={} roles={}",
                result.id(),
                result.roles()
        );
        return ApiResponse.success(result);
    }
}
