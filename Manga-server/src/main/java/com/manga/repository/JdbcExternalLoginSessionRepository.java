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

    /** 创建外部登录会话。 */
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

    /** 按登录令牌查询外部登录会话。 */
    @Override
    public Optional<ExternalLoginSession> findByLoginToken(String loginToken) {
        return Optional.ofNullable(sessionMapper.findByLoginToken(loginToken));
    }

    /** 判断登录场景是否仍在等待确认。 */
    @Override
    public boolean isWaitingScene(String sceneKey) {
        return sessionMapper.countWaitingByScene(
                ExternalIdentityProvider.WECHAT_OFFICIAL_ACCOUNT,
                sceneKey,
                ExternalLoginStatus.WAITING
        ) == 1;
    }

    /** 确认指定场景的外部登录会话。 */
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

    /** 消费已确认的外部登录会话。 */
    @Override
    public boolean consumeConfirmed(String loginToken, String actor) {
        return sessionMapper.consumeConfirmed(
                loginToken,
                actor,
                ExternalLoginStatus.CONFIRMED,
                ExternalLoginStatus.CONSUMED
        ) == 1;
    }

    /** 将等待中的外部登录会话标记为过期。 */
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
