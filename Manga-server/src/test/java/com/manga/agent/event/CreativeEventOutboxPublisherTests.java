package com.manga.agent.event;

import com.manga.agent.config.CreativeExecutionProperties;
import com.manga.agent.execution.CreativeOutboxBackoffPolicy;
import com.manga.agent.execution.CreativeRuntimeInstanceIdentity;
import com.manga.entity.CreativeEventOutbox;
import com.manga.repository.CreativeEventOutboxRepository;
import com.manga.repository.RedisKeyFactory;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 验证 Redis 故障不丢失 Event Outbox，且仍会唤醒本实例 SSE。
 */
class CreativeEventOutboxPublisherTests {

    /** Redis 发布失败后记录应退回 PENDING 退避，而不是误标为已发布。 */
    @Test
    void shouldRetryWithoutLosingLocalWakeupWhenRedisFails() {
        CreativeEventOutboxRepository repository = mock(CreativeEventOutboxRepository.class);
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        CreativeEventSignalHub signalHub = mock(CreativeEventSignalHub.class);
        RedisKeyFactory keyFactory = mock(RedisKeyFactory.class);
        CreativeRuntimeInstanceIdentity identity = new CreativeRuntimeInstanceIdentity();
        CreativeExecutionProperties properties = new CreativeExecutionProperties(
                Duration.ofSeconds(30), Duration.ofSeconds(15), Duration.ofSeconds(1),
                Duration.ofMinutes(1), Duration.ZERO, 100);
        CreativeEventOutbox outbox = CreativeEventOutbox.builder()
                .id(7L).runId("run-test").sequenceNo(9L).attempts(0).build();
        when(repository.findClaimCandidates(any(), eq(100))).thenReturn(List.of(outbox));
        when(repository.claim(eq(7L), anyString(), any(), any(), anyString())).thenReturn(true);
        when(keyFactory.creativeRunEventChannel("run-test")).thenReturn("test-channel");
        when(redisTemplate.convertAndSend("test-channel", "9"))
                .thenThrow(new IllegalStateException("redis unavailable"));
        CreativeEventOutboxPublisher publisher = new CreativeEventOutboxPublisher(
                repository, redisTemplate, signalHub, keyFactory, identity,
                new CreativeOutboxBackoffPolicy(properties), properties);

        publisher.publishPending();

        verify(signalHub).signal("run-test");
        verify(repository).markRetry(eq(7L), eq(identity.value()), any(),
                eq("IllegalStateException"), anyString());
        verify(repository, org.mockito.Mockito.never()).markPublished(anyLong(), anyString(), any(), anyString());
    }
}
