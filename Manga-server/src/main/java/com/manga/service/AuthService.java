package com.manga.service;

import com.manga.common.constant.SecurityConstants;
import com.manga.common.enums.AuthResponseCode;
import com.manga.common.enums.UserRole;
import com.manga.common.enums.UserStatus;
import com.manga.common.exception.BusinessException;
import com.manga.dto.AuthResponse;
import com.manga.dto.LoginRequest;
import com.manga.entity.UserAccount;
import com.manga.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.EnumSet;
import java.util.UUID;

/**
 * 处理账号密码登录和无账号游客登录。
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokenService;

    /**
     * 校验数据库账号和密码，成功后签发用户令牌。
     */
    public AuthResponse login(LoginRequest request) {
        UserAccount user = userRepository.findByUsername(request.username())
                .orElseThrow(() -> {
                    log.warn("Password login rejected");
                    return invalidCredentials();
                });
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            log.warn("Password login rejected");
            throw invalidCredentials();
        }
        if (user.getStatus() != UserStatus.ACTIVE) {
            log.warn("Password login rejected because account is unavailable userId={}", user.getId());
            throw new BusinessException(AuthResponseCode.ACCOUNT_UNAVAILABLE, HttpStatus.FORBIDDEN);
        }
        AuthResponse response = tokenService.issue(
                user.getId(),
                user.getUsername(),
                user.getDisplayName(),
                user.getRoles(),
                user.getRegistrationSource(),
                false
        );
        log.info("Password login succeeded userId={}", user.getId());
        return response;
    }

    /**
     * 创建不落库的临时游客身份并签发短期令牌。
     */
    public AuthResponse loginAsGuest() {
        String guestSuffix = UUID.randomUUID().toString().substring(0, SecurityConstants.GUEST_ID_LENGTH);
        String guestName = SecurityConstants.GUEST_USERNAME_PREFIX + guestSuffix;
        AuthResponse response = tokenService.issue(
                null,
                guestName,
                SecurityConstants.GUEST_DISPLAY_NAME_PREFIX + guestSuffix,
                EnumSet.of(UserRole.GUEST),
                null,
                true
        );
        log.debug("Guest login token issued");
        return response;
    }

    /** 创建统一的凭据无效异常。 */
    private BusinessException invalidCredentials() {
        return new BusinessException(AuthResponseCode.INVALID_CREDENTIALS, HttpStatus.UNAUTHORIZED);
    }
}
