package com.manga.repository;

import com.manga.common.enums.ExternalIdentityProvider;
import com.manga.entity.ExternalLoginSession;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * 定义外部扫码登录临时会话的存储契约。
 */
public interface ExternalLoginSessionRepository {

    /** 创建等待扫码确认的一次性登录会话。 */
    void create(
            String loginToken,
            ExternalIdentityProvider provider,
            String sceneKey,
            String providerTicket,
            String qrCodeUrl,
            LocalDateTime expiresAt,
            String actor);

    /** 按浏览器持有的一次性令牌查询扫码登录会话。 */
    Optional<ExternalLoginSession> findByLoginToken(String loginToken);

    /** 判断微信场景值是否对应仍在等待且未过期的会话。 */
    boolean isWaitingScene(String sceneKey);

    /** 使用微信场景值原子确认尚未过期的会话。 */
    boolean confirmByScene(String sceneKey, long userId, String actor);

    /** 原子消费已确认会话，避免同一登录结果被重复兑换。 */
    boolean consumeConfirmed(String loginToken, String actor);

    /** 将已到期且仍在等待的会话标记为过期。 */
    void expireWaiting(String loginToken, String actor);
}
