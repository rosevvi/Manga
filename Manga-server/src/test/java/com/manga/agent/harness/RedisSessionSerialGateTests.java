package com.manga.agent.harness;

import com.manga.agent.config.AgentHarnessProperties;
import com.manga.agent.domain.CreativeErrorCode;
import com.manga.repository.RedisKeyFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 验证状态会话闸门的互斥语义和 Redis 故障封闭策略。
 */
class RedisSessionSerialGateTests {

    private static final String STATE_SESSION_ID = "conversation-17";
    private static final String GATE_KEY = "manga:creative:agent-session:conversation-17:gate";
    private static final Duration SESSION_LEASE = Duration.ofSeconds(30);

    private StringRedisTemplate redisTemplate;
    private ValueOperations<String, String> valueOperations;
    private RedisSessionSerialGate gate;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        redisTemplate = mock(StringRedisTemplate.class);
        valueOperations = mock(ValueOperations.class);
        RedisKeyFactory keyFactory = mock(RedisKeyFactory.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(keyFactory.creativeAgentSessionGate(STATE_SESSION_ID)).thenReturn(GATE_KEY);
        gate = new RedisSessionSerialGate(
                redisTemplate,
                keyFactory,
                new AgentHarnessProperties(
                        SESSION_LEASE,
                        4,
                        Duration.ofSeconds(2),
                        Duration.ofSeconds(5)));
    }

    @Test
    void shouldAcquireLeaseWithRedisExpiration() {
        when(valueOperations.setIfAbsent(eq(GATE_KEY), any(String.class), eq(SESSION_LEASE)))
                .thenReturn(true);

        SessionGateLease lease = gate.acquire(STATE_SESSION_ID);

        assertThat(lease.stateSessionId()).isEqualTo(STATE_SESSION_ID);
        assertThat(lease.expiresAt()).isAfter(java.time.Instant.now());
        verify(valueOperations).setIfAbsent(eq(GATE_KEY), any(String.class), eq(SESSION_LEASE));
    }

    @Test
    void shouldRejectConcurrentLeaseForSameSession() {
        when(valueOperations.setIfAbsent(eq(GATE_KEY), any(String.class), eq(SESSION_LEASE)))
                .thenReturn(false);

        assertThatThrownBy(() -> gate.acquire(STATE_SESSION_ID))
                .isInstanceOfSatisfying(AgentRuntimeException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(CreativeErrorCode.STATE_SESSION_BUSY));
    }

    @Test
    void shouldFailClosedWhenRedisIsUnavailable() {
        when(valueOperations.setIfAbsent(eq(GATE_KEY), any(String.class), eq(SESSION_LEASE)))
                .thenThrow(new RedisConnectionFailureException("test Redis unavailable"));

        assertThatThrownBy(() -> gate.acquire(STATE_SESSION_ID))
                .isInstanceOfSatisfying(AgentRuntimeException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(CreativeErrorCode.STATE_STORE_UNAVAILABLE));
    }
}
