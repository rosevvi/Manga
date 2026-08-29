package com.manga.repository;

import com.manga.common.constant.ExceptionMessageConstants;
import com.manga.common.enums.ExternalIdentityProvider;
import com.manga.common.enums.ExternalLoginStatus;
import com.manga.config.properties.MangaRedisProperties;
import com.manga.entity.ExternalLoginSession;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 使用 Redis Hash、TTL 和 Lua 原子状态迁移保存扫码登录临时会话。
 */
@Repository
@RequiredArgsConstructor
@ConditionalOnProperty(
        prefix = "manga.auth.storage",
        name = "external-login-session",
        havingValue = "redis",
        matchIfMissing = true
)
public class RedisExternalLoginSessionRepository implements ExternalLoginSessionRepository {

    private static final String LOGIN_TOKEN_FIELD = "loginToken";
    private static final String PROVIDER_FIELD = "provider";
    private static final String SCENE_KEY_FIELD = "providerSceneKey";
    private static final String PROVIDER_TICKET_FIELD = "providerTicket";
    private static final String QR_URL_FIELD = "providerQrUrl";
    private static final String STATUS_FIELD = "status";
    private static final String USER_ID_FIELD = "userId";
    private static final String EXPIRES_AT_FIELD = "expiresAt";
    private static final String CONSUMED_AT_FIELD = "consumedAt";
    private static final String CREATED_AT_FIELD = "createdAt";
    private static final String UPDATED_AT_FIELD = "updatedAt";
    private static final String CREATED_BY_FIELD = "createdBy";
    private static final String UPDATED_BY_FIELD = "updatedBy";

    private static final RedisScript<Long> CONFIRM_SESSION_SCRIPT = RedisScript.of("""
            local status = redis.call('HGET', KEYS[1], ARGV[1])
            local expiresAt = redis.call('HGET', KEYS[1], ARGV[2])
            if status ~= ARGV[3] or not expiresAt or tonumber(expiresAt) <= tonumber(ARGV[4]) then
                return 0
            end
            redis.call('HSET', KEYS[1],
                ARGV[1], ARGV[5],
                ARGV[6], ARGV[7],
                ARGV[8], ARGV[4],
                ARGV[9], ARGV[10])
            return 1
            """, Long.class);

    private static final RedisScript<Long> CONSUME_SESSION_SCRIPT = RedisScript.of("""
            local status = redis.call('HGET', KEYS[1], ARGV[1])
            if status ~= ARGV[2] then
                return 0
            end
            redis.call('HSET', KEYS[1],
                ARGV[1], ARGV[3],
                ARGV[4], ARGV[5],
                ARGV[6], ARGV[5],
                ARGV[7], ARGV[8])
            return 1
            """, Long.class);

    private static final RedisScript<Long> EXPIRE_SESSION_SCRIPT = RedisScript.of("""
            local status = redis.call('HGET', KEYS[1], ARGV[1])
            local expiresAt = redis.call('HGET', KEYS[1], ARGV[2])
            if status ~= ARGV[3] or not expiresAt or tonumber(expiresAt) > tonumber(ARGV[4]) then
                return 0
            end
            redis.call('HSET', KEYS[1],
                ARGV[1], ARGV[5],
                ARGV[6], ARGV[4],
                ARGV[7], ARGV[8])
            return 1
            """, Long.class);

    private final StringRedisTemplate redisTemplate;
    private final RedisKeyFactory keyFactory;
    private final MangaRedisProperties redisProperties;

    @Override
    public void create(
            String loginToken,
            ExternalIdentityProvider provider,
            String sceneKey,
            String providerTicket,
            String qrCodeUrl,
            LocalDateTime expiresAt,
            String actor) {
        LocalDateTime now = LocalDateTime.now();
        Duration activeTime = Duration.between(now, expiresAt);
        if (activeTime.isZero() || activeTime.isNegative()) {
            throw new IllegalArgumentException(ExceptionMessageConstants.EXTERNAL_LOGIN_EXPIRATION_INVALID);
        }
        Duration storageTime = activeTime.plus(redisProperties.externalLoginExpiredRetention());
        String sessionKey = keyFactory.wechatLoginScene(sceneKey);
        String tokenKey = keyFactory.wechatLoginToken(loginToken);

        Map<String, String> values = new HashMap<>();
        values.put(LOGIN_TOKEN_FIELD, loginToken);
        values.put(PROVIDER_FIELD, provider.name());
        values.put(SCENE_KEY_FIELD, sceneKey);
        values.put(PROVIDER_TICKET_FIELD, providerTicket);
        values.put(QR_URL_FIELD, qrCodeUrl);
        values.put(STATUS_FIELD, ExternalLoginStatus.WAITING.name());
        values.put(EXPIRES_AT_FIELD, epochMillis(expiresAt));
        values.put(CREATED_AT_FIELD, epochMillis(now));
        values.put(UPDATED_AT_FIELD, epochMillis(now));
        values.put(CREATED_BY_FIELD, actor);
        values.put(UPDATED_BY_FIELD, actor);

        redisTemplate.opsForHash().putAll(sessionKey, values);
        redisTemplate.expire(sessionKey, storageTime);
        redisTemplate.opsForValue().set(tokenKey, sceneKey, storageTime);
    }

    @Override
    public Optional<ExternalLoginSession> findByLoginToken(String loginToken) {
        String tokenKey = keyFactory.wechatLoginToken(loginToken);
        String sceneKey = redisTemplate.opsForValue().get(tokenKey);
        if (sceneKey == null) {
            return Optional.empty();
        }
        Map<Object, Object> values = redisTemplate.opsForHash().entries(keyFactory.wechatLoginScene(sceneKey));
        if (values.isEmpty()) {
            redisTemplate.delete(tokenKey);
            return Optional.empty();
        }
        return Optional.of(toSession(values));
    }

    @Override
    public boolean isWaitingScene(String sceneKey) {
        Map<Object, Object> values = redisTemplate.opsForHash().entries(keyFactory.wechatLoginScene(sceneKey));
        if (values.isEmpty()) {
            return false;
        }
        return ExternalLoginStatus.WAITING.name().equals(value(values, STATUS_FIELD))
                && parseEpochMillis(values, EXPIRES_AT_FIELD).isAfter(LocalDateTime.now());
    }

    @Override
    public boolean confirmByScene(String sceneKey, long userId, String actor) {
        long now = System.currentTimeMillis();
        Long updated = redisTemplate.execute(
                CONFIRM_SESSION_SCRIPT,
                List.of(keyFactory.wechatLoginScene(sceneKey)),
                STATUS_FIELD,
                EXPIRES_AT_FIELD,
                ExternalLoginStatus.WAITING.name(),
                Long.toString(now),
                ExternalLoginStatus.CONFIRMED.name(),
                USER_ID_FIELD,
                Long.toString(userId),
                UPDATED_AT_FIELD,
                UPDATED_BY_FIELD,
                actor
        );
        return Long.valueOf(1L).equals(updated);
    }

    @Override
    public boolean consumeConfirmed(String loginToken, String actor) {
        String sceneKey = redisTemplate.opsForValue().get(keyFactory.wechatLoginToken(loginToken));
        if (sceneKey == null) {
            return false;
        }
        String now = Long.toString(System.currentTimeMillis());
        Long updated = redisTemplate.execute(
                CONSUME_SESSION_SCRIPT,
                List.of(keyFactory.wechatLoginScene(sceneKey)),
                STATUS_FIELD,
                ExternalLoginStatus.CONFIRMED.name(),
                ExternalLoginStatus.CONSUMED.name(),
                CONSUMED_AT_FIELD,
                now,
                UPDATED_AT_FIELD,
                UPDATED_BY_FIELD,
                actor
        );
        return Long.valueOf(1L).equals(updated);
    }

    @Override
    public void expireWaiting(String loginToken, String actor) {
        String sceneKey = redisTemplate.opsForValue().get(keyFactory.wechatLoginToken(loginToken));
        if (sceneKey == null) {
            return;
        }
        redisTemplate.execute(
                EXPIRE_SESSION_SCRIPT,
                List.of(keyFactory.wechatLoginScene(sceneKey)),
                STATUS_FIELD,
                EXPIRES_AT_FIELD,
                ExternalLoginStatus.WAITING.name(),
                Long.toString(System.currentTimeMillis()),
                ExternalLoginStatus.EXPIRED.name(),
                UPDATED_AT_FIELD,
                UPDATED_BY_FIELD,
                actor
        );
    }

    private ExternalLoginSession toSession(Map<Object, Object> values) {
        return ExternalLoginSession.builder()
                .loginToken(value(values, LOGIN_TOKEN_FIELD))
                .provider(ExternalIdentityProvider.valueOf(value(values, PROVIDER_FIELD)))
                .providerSceneKey(value(values, SCENE_KEY_FIELD))
                .providerTicket(value(values, PROVIDER_TICKET_FIELD))
                .providerQrUrl(value(values, QR_URL_FIELD))
                .status(ExternalLoginStatus.valueOf(value(values, STATUS_FIELD)))
                .userId(optionalLong(values, USER_ID_FIELD))
                .expiresAt(parseEpochMillis(values, EXPIRES_AT_FIELD))
                .consumedAt(optionalEpochMillis(values, CONSUMED_AT_FIELD))
                .createdAt(parseEpochMillis(values, CREATED_AT_FIELD))
                .updatedAt(parseEpochMillis(values, UPDATED_AT_FIELD))
                .createdBy(value(values, CREATED_BY_FIELD))
                .updatedBy(value(values, UPDATED_BY_FIELD))
                .build();
    }

    private String epochMillis(LocalDateTime dateTime) {
        return Long.toString(dateTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli());
    }

    private LocalDateTime parseEpochMillis(Map<Object, Object> values, String field) {
        long epochMillis = Long.parseLong(value(values, field));
        return LocalDateTime.ofInstant(Instant.ofEpochMilli(epochMillis), ZoneId.systemDefault());
    }

    private LocalDateTime optionalEpochMillis(Map<Object, Object> values, String field) {
        String rawValue = value(values, field);
        return rawValue == null ? null : LocalDateTime.ofInstant(
                Instant.ofEpochMilli(Long.parseLong(rawValue)),
                ZoneId.systemDefault()
        );
    }

    private Long optionalLong(Map<Object, Object> values, String field) {
        String rawValue = value(values, field);
        return rawValue == null ? null : Long.valueOf(rawValue);
    }

    private String value(Map<Object, Object> values, String field) {
        Object rawValue = values.get(field);
        return rawValue == null ? null : rawValue.toString();
    }
}
