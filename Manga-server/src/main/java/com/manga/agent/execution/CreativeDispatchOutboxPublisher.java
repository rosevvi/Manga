package com.manga.agent.execution;

import com.manga.agent.config.CreativeExecutionProperties;
import com.manga.agent.config.CreativeSchedulingConstants;
import com.manga.agent.domain.SystemActor;
import com.manga.entity.CreativeDispatchOutbox;
import com.manga.repository.CreativeDispatchOutboxRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * 竞争领取并投递持久化 Step 调度记录，为提交后立即调度提供崩溃恢复。
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class CreativeDispatchOutboxPublisher {

    private final CreativeDispatchOutboxRepository outboxRepository;
    private final CreativeStepDispatcher stepDispatcher;
    private final CreativeRuntimeInstanceIdentity instanceIdentity;
    private final CreativeOutboxBackoffPolicy backoffPolicy;
    private final CreativeExecutionProperties properties;

    /** 扫描并竞争领取到期调度记录，只有领取者可以推进该记录。 */
    @Scheduled(fixedDelayString = CreativeSchedulingConstants.DISPATCH_OUTBOX_INTERVAL)
    public void publishPending() {
        LocalDateTime now = LocalDateTime.now();
        String owner = instanceIdentity.value();
        for (CreativeDispatchOutbox outbox : outboxRepository.findClaimCandidates(now, properties.batchSize())) {
            if (!outboxRepository.claim(outbox.getId(), owner, now.plus(properties.outboxClaimLease()),
                    now, SystemActor.STEP_EXECUTION.code())) {
                continue;
            }
            publish(outbox, owner);
        }
    }

    /** 将已领取记录交给本地受控执行器并写入投递结果。 */
    private void publish(CreativeDispatchOutbox outbox, String owner) {
        try {
            stepDispatcher.dispatchAsync(outbox.getStepId(), outbox.getScheduleVersion());
            outboxRepository.markDispatched(outbox.getId(), owner, LocalDateTime.now(),
                    SystemActor.STEP_EXECUTION.code());
        } catch (RuntimeException exception) {
            int attempts = (Objects.isNull(outbox.getAttempts()) ? 0 : outbox.getAttempts()) + 1;
            outboxRepository.markRetry(outbox.getId(), owner,
                    LocalDateTime.now().plus(backoffPolicy.delay(attempts)),
                    exception.getClass().getSimpleName(), SystemActor.STEP_EXECUTION.code());
            log.warn("Creative dispatch outbox deferred stepId={} scheduleVersion={}",
                    outbox.getStepId(), outbox.getScheduleVersion());
        }
    }
}
