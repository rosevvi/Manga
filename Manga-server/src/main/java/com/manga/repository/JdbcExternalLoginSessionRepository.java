package com.manga.repository;

import com.manga.common.enums.ExternalIdentityProvider;
import com.manga.common.enums.ExternalLoginStatus;
import com.manga.entity.ExternalLoginSession;
import com.manga.mapper.ExternalLoginSessionMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * 在显式选择 JDBC 策略时使用 MyBatis-Plus 持久化扫码登录会话。
 */
@Repository
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "manga.auth.storage", name = "external-login-session", havingValue = "jdbc")
public class JdbcExternalLoginSessionRepository implements ExternalLoginSessionRepository {

    private final ExternalLoginSessionMapper sessionMapper;

    @Override
    public void create(
            String loginToken,
            ExternalIdentityProvider provider,
            String sceneKey,
            String providerTicket,
            String qrCodeUrl,
            LocalDateTime expiresAt,
            String actor) {
        sessionMapper.insert(ExternalLoginSession.builder()
                .loginToken(loginToken)
                .provider(provider)
                .providerSceneKey(sceneKey)
                .providerTicket(providerTicket)
                .providerQrUrl(qrCodeUrl)
                .status(ExternalLoginStatus.WAITING)
                .expiresAt(expiresAt)
                .createdBy(actor)
                .updatedBy(actor)
                .build());
    }

    @Override
    public Optional<ExternalLoginSession> findByLoginToken(String loginToken) {
        return Optional.ofNullable(sessionMapper.findByLoginToken(loginToken));
    }

    @Override
    public boolean isWaitingScene(String sceneKey) {
        return sessionMapper.countWaitingByScene(
                ExternalIdentityProvider.WECHAT_OFFICIAL_ACCOUNT,
                sceneKey,
                ExternalLoginStatus.WAITING
        ) == 1;
    }

    @Override
    public boolean confirmByScene(String sceneKey, long userId, String actor) {
        return sessionMapper.confirmByScene(
                sceneKey,
                userId,
                actor,
                ExternalIdentityProvider.WECHAT_OFFICIAL_ACCOUNT,
                ExternalLoginStatus.WAITING,
                ExternalLoginStatus.CONFIRMED
        ) == 1;
    }

    @Override
    public boolean consumeConfirmed(String loginToken, String actor) {
        return sessionMapper.consumeConfirmed(
                loginToken,
                actor,
                ExternalLoginStatus.CONFIRMED,
                ExternalLoginStatus.CONSUMED
        ) == 1;
    }

    @Override
    public void expireWaiting(String loginToken, String actor) {
        sessionMapper.expireWaiting(
                loginToken,
                actor,
                ExternalLoginStatus.WAITING,
                ExternalLoginStatus.EXPIRED
        );
    }
}
