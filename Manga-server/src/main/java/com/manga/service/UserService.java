package com.manga.service;

import com.manga.common.enums.AuthResponseCode;
import com.manga.common.enums.UserRole;
import com.manga.common.exception.BusinessException;
import com.manga.common.security.AuthenticatedUser;
import com.manga.common.security.SecurityUtils;
import com.manga.dto.ChangePasswordRequest;
import com.manga.dto.CurrentUserResponse;
import com.manga.dto.UpdateCurrentUserRequest;
import com.manga.dto.UpdateUserRolesRequest;
import com.manga.dto.UserSummaryResponse;
import com.manga.entity.UserAccount;
import com.manga.repository.RoleRepository;
import com.manga.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * 提供当前用户读取、用户列表和角色分配能力。
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;

    /**
     * 从当前安全上下文构造用户或游客身份信息。
     */
    public CurrentUserResponse getCurrentUser() {
        AuthenticatedUser authenticatedUser = SecurityUtils.requireCurrentUser();
        if (authenticatedUser.guest()) {
            return guestResponse(authenticatedUser);
        }
        return toCurrentUser(findCurrentAccount(authenticatedUser));
    }

    /**
     * 更新当前数据库用户的显示名称。
     */
    @Transactional
    public CurrentUserResponse updateCurrentUser(UpdateCurrentUserRequest request) {
        AuthenticatedUser authenticatedUser = SecurityUtils.requireCurrentUser();
        UserAccount user = findCurrentAccount(authenticatedUser);
        userRepository.updateDisplayName(user.getId(), request.displayName().trim(), authenticatedUser.username());
        log.info("User profile updated userId={} actor={}", user.getId(), authenticatedUser.username());
        return toCurrentUser(userRepository.findById(user.getId()).orElseThrow());
    }

    /**
     * 校验当前密码后保存新的 BCrypt 密码摘要。
     */
    @Transactional
    public void changePassword(ChangePasswordRequest request) {
        AuthenticatedUser authenticatedUser = SecurityUtils.requireCurrentUser();
        UserAccount user = findCurrentAccount(authenticatedUser);
        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            log.warn("Password change rejected userId={}", user.getId());
            throw new BusinessException(AuthResponseCode.CURRENT_PASSWORD_INVALID, HttpStatus.UNAUTHORIZED);
        }
        userRepository.updatePasswordHash(
                user.getId(),
                passwordEncoder.encode(request.newPassword()),
                authenticatedUser.username()
        );
        log.info("Password changed userId={} actor={}", user.getId(), authenticatedUser.username());
    }

    private CurrentUserResponse guestResponse(AuthenticatedUser user) {
        return new CurrentUserResponse(
                user.userId(),
                user.username(),
                user.displayName(),
                user.guest(),
                user.roles(),
                null,
                null,
                null,
                null
        );
    }

    private UserAccount findCurrentAccount(AuthenticatedUser authenticatedUser) {
        if (authenticatedUser.guest()) {
            throw new BusinessException(AuthResponseCode.GUEST_PROFILE_READ_ONLY, HttpStatus.FORBIDDEN);
        }
        if (authenticatedUser.userId() == null) {
            throw new BusinessException(AuthResponseCode.USER_NOT_FOUND, HttpStatus.NOT_FOUND);
        }
        return userRepository.findById(authenticatedUser.userId())
                .orElseThrow(() -> new BusinessException(AuthResponseCode.USER_NOT_FOUND, HttpStatus.NOT_FOUND));
    }

    private CurrentUserResponse toCurrentUser(UserAccount user) {
        return new CurrentUserResponse(
                user.getId(),
                user.getUsername(),
                user.getDisplayName(),
                false,
                user.getRoles().stream().map(UserRole::name).sorted().toList(),
                user.getStatus().name(),
                user.getRegistrationSource().name(),
                user.getCreatedAt(),
                user.getUpdatedAt()
        );
    }

    /**
     * 返回数据库用户列表，不暴露密码摘要。
     */
    public List<UserSummaryResponse> findAll() {
        return userRepository.findAll().stream().map(this::toSummary).toList();
    }

    /**
     * 替换指定用户角色并返回更新后的用户信息。
     */
    @Transactional
    public UserSummaryResponse replaceRoles(long userId, UpdateUserRolesRequest request) {
        String actor = SecurityUtils.requireCurrentUsername();
        UserAccount user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(AuthResponseCode.USER_NOT_FOUND, HttpStatus.NOT_FOUND));
        Set<UserRole> roles = parseAssignableRoles(request.roleCodes());
        roleRepository.replaceUserRoles(userId, roles, actor);
        log.info("User roles replaced userId={} actor={} roles={}", userId, actor, roles);
        return toSummary(userRepository.findById(user.getId()).orElseThrow());
    }

    private Set<UserRole> parseAssignableRoles(Set<String> roleCodes) {
        EnumSet<UserRole> roles = EnumSet.noneOf(UserRole.class);
        try {
            for (String roleCode : roleCodes) {
                UserRole role = UserRole.valueOf(roleCode);
                if (!role.assignable()) {
                    throw new IllegalArgumentException();
                }
                roles.add(role);
            }
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(AuthResponseCode.INVALID_ROLE);
        }
        return roles;
    }

    private UserSummaryResponse toSummary(UserAccount user) {
        return new UserSummaryResponse(
                user.getId(),
                user.getUsername(),
                user.getDisplayName(),
                user.getStatus().name(),
                user.getRegistrationSource().name(),
                user.getRoles().stream().map(UserRole::name).sorted().toList(),
                user.getCreatedAt(),
                user.getUpdatedAt(),
                user.getCreatedBy(),
                user.getUpdatedBy()
        );
    }
}
