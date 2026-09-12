package com.manga.agent;

import com.manga.entity.AgentEventOutbox;
import com.manga.repository.AgentEventOutboxRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/** 将已提交的事件日志异步发布为 Redis 唤醒信号。 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AgentEventOutboxPublisher {

    private static final String CHANNEL_PREFIX = "manga:agent:run:";

    private final AgentEventOutboxRepository outboxRepository;
    private final StringRedisTemplate redisTemplate;
    private final AgentEventSignalHub signalHub;

    @Scheduled(fixedDelay = 1000)
    public void publishPending() {
        for (AgentEventOutbox outbox : outboxRepository.findPending(100)) {
            try {
                redisTemplate.convertAndSend(CHANNEL_PREFIX + outbox.getRunId(), String.valueOf(outbox.getSequenceNo()));
                signalHub.signal(outbox.getRunId());
                outbox.setStatus("PUBLISHED");
                outbox.setPublishedAt(LocalDateTime.now());
                outbox.setAttempts((outbox.getAttempts() == null ? 0 : outbox.getAttempts()) + 1);
                outbox.setLastError(null);
                outboxRepository.update(outbox);
            } catch (RuntimeException exception) {
                outbox.setAttempts((outbox.getAttempts() == null ? 0 : outbox.getAttempts()) + 1);
                outbox.setLastError(exception.getClass().getSimpleName());
                outboxRepository.update(outbox);
                log.debug("Agent event outbox publish deferred runId={} sequence={}", outbox.getRunId(), outbox.getSequenceNo());
            }
        }
    }
}
