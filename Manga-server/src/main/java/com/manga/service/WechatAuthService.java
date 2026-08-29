package com.manga.service;

import com.manga.common.constant.AuditConstants;
import com.manga.common.constant.ExceptionMessageConstants;
import com.manga.common.constant.WechatConstants;
import com.manga.common.enums.AuthResponseCode;
import com.manga.common.enums.ExternalIdentityProvider;
import com.manga.common.enums.ExternalLoginStatus;
import com.manga.common.enums.RegistrationSource;
import com.manga.common.enums.UserRole;
import com.manga.common.enums.UserStatus;
import com.manga.common.exception.BusinessException;
import com.manga.config.properties.WechatOfficialAccountProperties;
import com.manga.dto.AuthResponse;
import com.manga.dto.WechatQrLoginResponse;
import com.manga.dto.WechatQrLoginStatusResponse;
import com.manga.entity.ExternalLoginSession;
import com.manga.entity.UserAccount;
import com.manga.integration.wechat.WechatApiException;
import com.manga.integration.wechat.WechatCallbackMessage;
import com.manga.integration.wechat.WechatCallbackParser;
import com.manga.integration.wechat.WechatOfficialAccountClient;
import com.manga.integration.wechat.WechatQrCode;
import com.manga.repository.ExternalIdentityRepository;
import com.manga.repository.ExternalLoginSessionRepository;
import com.manga.repository.RoleRepository;
import com.manga.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.HexFormat;
import java.util.UUID;

/**
 * 编排微信公众号二维码创建、事件确认和一次性令牌兑换流程。
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class WechatAuthService {

    private final WechatOfficialAccountClient wechatClient;
    private final WechatOfficialAccountProperties properties;
    private final WechatCallbackParser callbackParser;
    private final ExternalLoginSessionRepository loginSessionRepository;
    private final ExternalIdentityRepository identityRepository;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final TokenService tokenService;

    /**
     * 创建微信公众号临时二维码和对应的一次性轮询会话。
     */
    public WechatQrLoginResponse createQrLogin() {
        validateConfiguration();
        String loginToken = UUID.randomUUID().toString();
        String sceneKey = createSceneKey();
        try {
            WechatQrCode qrCode = wechatClient.createTemporaryQrCode(sceneKey, properties.qrLoginTtl());
            long expiresIn = Math.min(properties.qrLoginTtl().toSeconds(), qrCode.expiresIn());
            loginSessionRepository.create(
                    loginToken,
                    ExternalIdentityProvider.WECHAT_OFFICIAL_ACCOUNT,
                    sceneKey,
                    qrCode.ticket(),
                    qrCode.qrCodeUrl(),
                    LocalDateTime.now().plusSeconds(expiresIn),
                    AuditConstants.WECHAT_OFFICIAL_ACCOUNT_ACTOR
            );
            log.info("WeChat QR login session created expiresInSeconds={}", expiresIn);
            return new WechatQrLoginResponse(
                    loginToken,
                    qrCode.qrCodeUrl(),
                    expiresIn,
                    properties.pollInterval().toSeconds()
            );
        } catch (WechatApiException exception) {
            log.warn("Failed to create WeChat login QR code", exception);
            throw new BusinessException(AuthResponseCode.WECHAT_SERVICE_UNAVAILABLE, HttpStatus.BAD_GATEWAY);
        }
    }

    /**
     * 查询扫码进度，并仅在首次确认时将会话兑换为 Manga JWT。
     */
    @Transactional
    public WechatQrLoginStatusResponse queryQrLogin(String loginToken) {
        ExternalLoginSession session = loginSessionRepository.findByLoginToken(loginToken)
                .orElseThrow(() -> new BusinessException(
                        AuthResponseCode.EXTERNAL_LOGIN_NOT_FOUND,
                        HttpStatus.NOT_FOUND
                ));
        LocalDateTime now = LocalDateTime.now();
        if (session.getStatus() == ExternalLoginStatus.WAITING && !session.getExpiresAt().isAfter(now)) {
            loginSessionRepository.expireWaiting(loginToken, AuditConstants.WECHAT_OFFICIAL_ACCOUNT_ACTOR);
            log.info("WeChat QR login session expired");
            return statusResponse(ExternalLoginStatus.EXPIRED, 0, null);
        }
        long expiresIn = Math.max(0, Duration.between(now, session.getExpiresAt()).toSeconds());
        if (session.getStatus() != ExternalLoginStatus.CONFIRMED) {
            return statusResponse(session.getStatus(), expiresIn, null);
        }
        UserAccount user = userRepository.findById(session.getUserId())
                .orElseThrow(() -> new BusinessException(AuthResponseCode.USER_NOT_FOUND, HttpStatus.NOT_FOUND));
        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new BusinessException(AuthResponseCode.ACCOUNT_UNAVAILABLE, HttpStatus.FORBIDDEN);
        }
        if (!loginSessionRepository.consumeConfirmed(
                loginToken,
                AuditConstants.WECHAT_OFFICIAL_ACCOUNT_ACTOR
        )) {
            log.debug("WeChat QR login session was already consumed");
            return statusResponse(ExternalLoginStatus.CONSUMED, expiresIn, null);
        }
        AuthResponse authentication = tokenService.issue(
                user.getId(),
                user.getUsername(),
                user.getDisplayName(),
                user.getRoles(),
                user.getRegistrationSource(),
                false
        );
        log.info("WeChat QR login session consumed userId={}", user.getId());
        return statusResponse(ExternalLoginStatus.CONFIRMED, expiresIn, authentication);
    }

    /**
     * 处理关注或已关注用户扫码事件，并确认对应登录会话。
     */
    @Transactional
    public void handleCallback(String callbackXml) {
        WechatCallbackMessage message;
        try {
            message = callbackParser.parse(callbackXml);
        } catch (WechatApiException exception) {
            log.warn("Invalid WeChat callback payload rejected");
            throw new BusinessException(AuthResponseCode.WECHAT_CALLBACK_INVALID, HttpStatus.BAD_REQUEST);
        }
        if (!WechatConstants.MESSAGE_TYPE_EVENT.equalsIgnoreCase(message.messageType())
                || (!WechatConstants.EVENT_SUBSCRIBE.equalsIgnoreCase(message.event())
                && !WechatConstants.EVENT_SCAN.equalsIgnoreCase(message.event()))) {
            return;
        }
        String sceneKey = normalizeSceneKey(message.eventKey());
        if (!StringUtils.hasText(message.fromUserName())
                || !StringUtils.hasText(sceneKey)
                || !loginSessionRepository.isWaitingScene(sceneKey)) {
            return;
        }
        long userId = findOrCreateWechatUser(message.fromUserName());
        boolean confirmed = loginSessionRepository.confirmByScene(
                sceneKey,
                userId,
                AuditConstants.WECHAT_OFFICIAL_ACCOUNT_ACTOR
        );
        if (confirmed) {
            log.info("WeChat callback confirmed login session userId={}", userId);
        }
    }

    private long findOrCreateWechatUser(String openId) {
        return identityRepository.findUserId(ExternalIdentityProvider.WECHAT_OFFICIAL_ACCOUNT, openId)
                .orElseGet(() -> createWechatUser(openId));
    }

    private long createWechatUser(String openId) {
        String actor = AuditConstants.WECHAT_OFFICIAL_ACCOUNT_ACTOR;
        long userId = userRepository.create(
                createWechatUsername(openId),
                null,
                createWechatDisplayName(openId),
                UserStatus.ACTIVE,
                RegistrationSource.WECHAT_OFFICIAL_ACCOUNT,
                actor
        );
        identityRepository.create(
                userId,
                ExternalIdentityProvider.WECHAT_OFFICIAL_ACCOUNT,
                openId,
                null,
                actor
        );
        roleRepository.replaceUserRoles(userId, EnumSet.of(UserRole.USER), actor);
        log.info("WeChat user created userId={}", userId);
        return userId;
    }

    private String createWechatUsername(String openId) {
        try {
            byte[] hash = MessageDigest.getInstance(WechatConstants.SHA_256_ALGORITHM)
                    .digest(openId.getBytes(StandardCharsets.UTF_8));
            return WechatConstants.WECHAT_USERNAME_PREFIX
                    + HexFormat.of().formatHex(hash).substring(0, WechatConstants.USERNAME_HASH_LENGTH);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(
                    ExceptionMessageConstants.ALGORITHM_UNAVAILABLE_TEMPLATE
                            .formatted(WechatConstants.SHA_256_ALGORITHM),
                    exception
            );
        }
    }

    private String createWechatDisplayName(String openId) {
        int start = Math.max(0, openId.length() - WechatConstants.DISPLAY_NAME_SUFFIX_LENGTH);
        return WechatConstants.WECHAT_DISPLAY_NAME_PREFIX + openId.substring(start);
    }

    private String createSceneKey() {
        String randomPart = UUID.randomUUID().toString().replace("-", "");
        String prefix = properties.scenePrefix();
        if (!StringUtils.hasText(prefix)
                || prefix.length() + randomPart.length() > WechatConstants.SCENE_KEY_MAX_LENGTH) {
            throw new BusinessException(AuthResponseCode.WECHAT_CONFIGURATION_UNAVAILABLE);
        }
        return prefix + randomPart;
    }

    private String normalizeSceneKey(String eventKey) {
        if (!StringUtils.hasText(eventKey)) {
            return null;
        }
        return eventKey.startsWith(WechatConstants.SUBSCRIBE_EVENT_KEY_PREFIX)
                ? eventKey.substring(WechatConstants.SUBSCRIBE_EVENT_KEY_PREFIX.length())
                : eventKey;
    }

    private void validateConfiguration() {
        if (!StringUtils.hasText(properties.appId())
                || !StringUtils.hasText(properties.secret())
                || !StringUtils.hasText(properties.token())
                || properties.qrLoginTtl() == null
                || properties.qrLoginTtl().isZero()
                || properties.qrLoginTtl().isNegative()
                || properties.pollInterval() == null
                || properties.pollInterval().isZero()
                || properties.pollInterval().isNegative()) {
            throw new BusinessException(AuthResponseCode.WECHAT_CONFIGURATION_UNAVAILABLE);
        }
    }

    private WechatQrLoginStatusResponse statusResponse(
            ExternalLoginStatus status,
            long expiresIn,
            AuthResponse authentication) {
        return new WechatQrLoginStatusResponse(status.name(), expiresIn, authentication);
    }
}
