package com.manga.agent.event;

import com.manga.agent.domain.CreativeOutboxStatus;
import com.manga.agent.domain.SystemActor;
import com.manga.entity.CreativeEventOutbox;
import com.manga.repository.CreativeEventOutboxRepository;
import com.manga.repository.RedisKeyFactory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * 将已提交的事件日志异步发布为 Redis 唤醒信号。
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class CreativeEventOutboxPublisher {

    private static final int PUBLISH_BATCH_SIZE = 100;

    private final CreativeEventOutboxRepository outboxRepository;
    private final StringRedisTemplate redisTemplate;
    private final CreativeEventSignalHub signalHub;
    private final RedisKeyFactory redisKeyFactory;

    /** 发布待处理唤醒信号；Redis 失败时保留记录供下一轮重试。 */
    @Scheduled(fixedDelayString = "${manga.creative.event-outbox.publish-interval:1s}")
    public void publishPending() {
        for (CreativeEventOutbox outbox : outboxRepository.findPending(PUBLISH_BATCH_SIZE)) {
            publish(outbox);
        }
    }

    /** 发布单条唤醒记录并更新投递审计信息。 */
    private void publish(CreativeEventOutbox outbox) {
        try {
            redisTemplate.convertAndSend(redisKeyFactory.creativeRunEventChannel(outbox.getRunId()),
                    String.valueOf(outbox.getSequenceNo()));
            signalHub.signal(outbox.getRunId());
            outbox.setStatus(CreativeOutboxStatus.PUBLISHED);
            outbox.setPublishedAt(LocalDateTime.now());
            outbox.setAttempts(nextAttempt(outbox));
            outbox.setLastError(null);
            outbox.setUpdatedBy(SystemActor.EVENT_OUTBOX.code());
            outboxRepository.update(outbox);
        } catch (RuntimeException exception) {
            outbox.setAttempts(nextAttempt(outbox));
            outbox.setLastError(exception.getClass().getSimpleName());
            outbox.setUpdatedBy(SystemActor.EVENT_OUTBOX.code());
            outboxRepository.update(outbox);
            log.debug("Creative event outbox publish deferred runId={} sequence={}",
                    outbox.getRunId(), outbox.getSequenceNo());
        }
    }

    /** 返回包含本次投递的累计尝试次数。 */
    private int nextAttempt(CreativeEventOutbox outbox) {
        return (Objects.isNull(outbox.getAttempts()) ? 0 : outbox.getAttempts()) + 1;
    }
}
