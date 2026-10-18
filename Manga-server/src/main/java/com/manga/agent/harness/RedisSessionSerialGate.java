package com.manga.agent.harness;

import com.manga.agent.config.AgentHarnessProperties;
import com.manga.agent.domain.CreativeErrorCode;
import com.manga.agent.domain.CreativeRuntimeMessages;
import com.manga.repository.RedisKeyFactory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 使用 Redis SET NX、TTL 和令牌校验脚本实现集群级状态会话串行闸门。
 */
@Component
@Slf4j
public class RedisSessionSerialGate implements SessionSerialGate {

    /** 仅持有相同令牌时续期，避免旧执行器延长新执行器的租约。 */
    private static final RedisScript<Long> RENEW_SCRIPT = RedisScript.of("""
            if redis.call('GET', KEYS[1]) ~= ARGV[1] then
                return 0
            end
            redis.call('PEXPIRE', KEYS[1], ARGV[2])
            return 1
            """, Long.class);
    /** 仅持有相同令牌时释放，避免旧执行器删除新执行器的租约。 */
    private static final RedisScript<Long> RELEASE_SCRIPT = RedisScript.of("""
            if redis.call('GET', KEYS[1]) ~= ARGV[1] then
                return 0
            end
            return redis.call('DEL', KEYS[1])
            """, Long.class);

    private final StringRedisTemplate redisTemplate;
    private final RedisKeyFactory keyFactory;
    private final Duration sessionLease;

    public RedisSessionSerialGate(
            StringRedisTemplate redisTemplate,
            RedisKeyFactory keyFactory,
            AgentHarnessProperties properties) {
        this.redisTemplate = redisTemplate;
        this.keyFactory = keyFactory;
        this.sessionLease = properties.sessionLease();
    }

    /** 获取集群级独占租约；Redis 故障时封闭失败，绝不放行模型调用。 */
    @Override
    public SessionGateLease acquire(String stateSessionId) {
        if (!StringUtils.hasText(stateSessionId)) {
            throw new IllegalArgumentException(CreativeRuntimeMessages.AGENT_STATE_SESSION_ID_REQUIRED);
        }
        String key = keyFactory.creativeAgentSessionGate(stateSessionId);
        String token = UUID.randomUUID().toString();
        try {
            Boolean acquired = redisTemplate.opsForValue().setIfAbsent(key, token, sessionLease);
            if (!Boolean.TRUE.equals(acquired)) {
                throw new AgentRuntimeException(
                        CreativeErrorCode.STATE_SESSION_BUSY,
                        CreativeRuntimeMessages.AGENT_STATE_SESSION_BUSY);
            }
            return new RedisSessionGateLease(stateSessionId, key, token);
        } catch (DataAccessException exception) {
            throw unavailable(exception);
        }
    }

    /** 将 Redis 连接或脚本错误统一转换为可归类的封闭失败。 */
    private AgentRuntimeException unavailable(DataAccessException cause) {
        return new AgentRuntimeException(
                CreativeErrorCode.STATE_STORE_UNAVAILABLE,
                CreativeRuntimeMessages.AGENT_STATE_SESSION_GATE_UNAVAILABLE,
                cause);
    }

    /** Redis 状态会话租约实现，令牌仅保留在进程内且不写入日志。 */
    private final class RedisSessionGateLease implements SessionGateLease {

        private final String stateSessionId;
        private final String key;
        private final String token;
        private final AtomicBoolean closed = new AtomicBoolean();
        private volatile Instant expiresAt;

        private RedisSessionGateLease(String stateSessionId, String key, String token) {
            this.stateSessionId = stateSessionId;
            this.key = key;
            this.token = token;
            this.expiresAt = Instant.now().plus(sessionLease);
        }

        /** 返回当前租约保护的状态会话标识。 */
        @Override
        public String stateSessionId() {
            return stateSessionId;
        }

        /** 返回本地观测到的租约截止时间。 */
        @Override
        public Instant expiresAt() {
            return expiresAt;
        }

        /** 使用持有令牌原子续期；Redis 故障或令牌失效时中止后续执行。 */
        @Override
        public void renew() {
            if (closed.get()) {
                throw leaseLost();
            }
            try {
                Long renewed = redisTemplate.execute(
                        RENEW_SCRIPT,
                        List.of(key),
                        token,
                        Long.toString(sessionLease.toMillis()));
                if (!Long.valueOf(1L).equals(renewed)) {
                    throw leaseLost();
                }
                expiresAt = Instant.now().plus(sessionLease);
            } catch (DataAccessException exception) {
                throw unavailable(exception);
            }
        }

        /** 幂等释放当前令牌持有的闸门；租约已自然过期时无需补偿。 */
        @Override
        public void close() {
            if (!closed.compareAndSet(false, true)) {
                return;
            }
            try {
                redisTemplate.execute(RELEASE_SCRIPT, List.of(key), token);
            } catch (DataAccessException exception) {
                log.warn("Failed to release Agent state session gate stateSessionId={}",
                        stateSessionId, exception);
            }
        }

        /** 创建不含租约令牌的稳定租约丢失异常。 */
        private AgentRuntimeException leaseLost() {
            return new AgentRuntimeException(
                    CreativeErrorCode.OWNER_LEASE_LOST,
                    CreativeRuntimeMessages.AGENT_STATE_SESSION_LEASE_LOST);
        }
    }
}
