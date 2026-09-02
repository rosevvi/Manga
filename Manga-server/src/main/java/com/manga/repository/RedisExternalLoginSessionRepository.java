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

    /** 登录令牌哈希字段名。 */
    private static final String LOGIN_TOKEN_FIELD = "loginToken";
    /** 身份提供方哈希字段名。 */
    private static final String PROVIDER_FIELD = "provider";
    /** 登录场景哈希字段名。 */
    private static final String SCENE_KEY_FIELD = "providerSceneKey";
    /** 二维码票据哈希字段名。 */
    private static final String PROVIDER_TICKET_FIELD = "providerTicket";
    /** 二维码地址哈希字段名。 */
    private static final String QR_URL_FIELD = "providerQrUrl";
    /** 登录状态哈希字段名。 */
    private static final String STATUS_FIELD = "status";
    /** 确认用户主键哈希字段名。 */
    private static final String USER_ID_FIELD = "userId";
    /** 会话过期时间哈希字段名。 */
    private static final String EXPIRES_AT_FIELD = "expiresAt";
    /** 会话消费时间哈希字段名。 */
    private static final String CONSUMED_AT_FIELD = "consumedAt";
    /** 会话创建时间哈希字段名。 */
    private static final String CREATED_AT_FIELD = "createdAt";
    /** 会话更新时间哈希字段名。 */
    private static final String UPDATED_AT_FIELD = "updatedAt";
    /** 会话创建人哈希字段名。 */
    private static final String CREATED_BY_FIELD = "createdBy";
    /** 会话更新人哈希字段名。 */
    private static final String UPDATED_BY_FIELD = "updatedBy";

    /** 原子确认等待中登录会话的 Lua 脚本。 */
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

    /** 原子消费已确认登录会话的 Lua 脚本。 */
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

    /** 原子标记等待中登录会话过期的 Lua 脚本。 */
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

    /** 按登录令牌查询外部登录会话。 */
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

    /** 判断登录场景是否仍在等待确认。 */
    @Override
    public boolean isWaitingScene(String sceneKey) {
        Map<Object, Object> values = redisTemplate.opsForHash().entries(keyFactory.wechatLoginScene(sceneKey));
        if (values.isEmpty()) {
            return false;
        }
        return ExternalLoginStatus.WAITING.name().equals(value(values, STATUS_FIELD))
                && parseEpochMillis(values, EXPIRES_AT_FIELD).isAfter(LocalDateTime.now());
    }

    /** 确认指定场景的外部登录会话。 */
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

    /** 消费已确认的外部登录会话。 */
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

    /** 将等待中的外部登录会话标记为过期。 */
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

    /** 将 Redis 字段转换为登录会话。 */
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

    /** 将时间转换为毫秒时间戳文本。 */
    private String epochMillis(LocalDateTime dateTime) {
        return Long.toString(dateTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli());
    }

    /** 解析必填毫秒时间戳。 */
    private LocalDateTime parseEpochMillis(Map<Object, Object> values, String field) {
        long epochMillis = Long.parseLong(value(values, field));
        return LocalDateTime.ofInstant(Instant.ofEpochMilli(epochMillis), ZoneId.systemDefault());
    }

    /** 解析可选毫秒时间戳。 */
    private LocalDateTime optionalEpochMillis(Map<Object, Object> values, String field) {
        String rawValue = value(values, field);
        return rawValue == null ? null : LocalDateTime.ofInstant(
                Instant.ofEpochMilli(Long.parseLong(rawValue)),
                ZoneId.systemDefault()
        );
    }

    /** 解析可选长整数。 */
    private Long optionalLong(Map<Object, Object> values, String field) {
        String rawValue = value(values, field);
        return rawValue == null ? null : Long.valueOf(rawValue);
    }

    /** 读取 Redis 字段文本。 */
    private String value(Map<Object, Object> values, String field) {
        Object rawValue = values.get(field);
        return rawValue == null ? null : rawValue.toString();
    }
}
