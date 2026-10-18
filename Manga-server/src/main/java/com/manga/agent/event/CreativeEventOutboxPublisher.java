package com.manga.agent.event;

import com.manga.agent.config.CreativeExecutionProperties;
import com.manga.agent.config.CreativeSchedulingConstants;
import com.manga.agent.domain.SystemActor;
import com.manga.agent.execution.CreativeOutboxBackoffPolicy;
import com.manga.agent.execution.CreativeRuntimeInstanceIdentity;
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
 * 竞争领取 Event Outbox，并把数据库事件发布为进程内及 Redis 唤醒信号。
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class CreativeEventOutboxPublisher {

    private final CreativeEventOutboxRepository outboxRepository;
    private final StringRedisTemplate redisTemplate;
    private final CreativeEventSignalHub signalHub;
    private final RedisKeyFactory redisKeyFactory;
    private final CreativeRuntimeInstanceIdentity instanceIdentity;
    private final CreativeOutboxBackoffPolicy backoffPolicy;
    private final CreativeExecutionProperties properties;

    /** 扫描并原子领取到期记录，领取租约允许实例崩溃后由其他实例接管。 */
    @Scheduled(fixedDelayString = CreativeSchedulingConstants.EVENT_OUTBOX_INTERVAL)
    public void publishPending() {
        LocalDateTime now = LocalDateTime.now();
        String owner = instanceIdentity.value();
        for (CreativeEventOutbox outbox : outboxRepository.findClaimCandidates(now, properties.batchSize())) {
            if (!outboxRepository.claim(outbox.getId(), owner, now.plus(properties.outboxClaimLease()),
                    now, SystemActor.EVENT_OUTBOX.code())) {
                continue;
            }
            publish(outbox, owner);
        }
    }

    /** 发布单条唤醒记录；Redis 故障不会阻断本实例 SSE，数据库游标仍是唯一事实源。 */
    private void publish(CreativeEventOutbox outbox, String owner) {
        signalHub.signal(outbox.getRunId());
        try {
            redisTemplate.convertAndSend(redisKeyFactory.creativeRunEventChannel(outbox.getRunId()),
                    String.valueOf(outbox.getSequenceNo()));
            outboxRepository.markPublished(outbox.getId(), owner, LocalDateTime.now(),
                    SystemActor.EVENT_OUTBOX.code());
        } catch (RuntimeException exception) {
            int attempts = (Objects.isNull(outbox.getAttempts()) ? 0 : outbox.getAttempts()) + 1;
            outboxRepository.markRetry(outbox.getId(), owner,
                    LocalDateTime.now().plus(backoffPolicy.delay(attempts)),
                    exception.getClass().getSimpleName(), SystemActor.EVENT_OUTBOX.code());
            log.warn("Creative event outbox deferred runId={} sequence={}",
                    outbox.getRunId(), outbox.getSequenceNo());
        }
    }
}
