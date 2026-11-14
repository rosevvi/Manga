package com.manga.agent.execution;

import com.manga.agent.application.CreativeRunAggregationService;
import com.manga.agent.application.CreativeEventService;
import com.manga.agent.application.CreativeIdentifierGenerator;
import com.manga.agent.collaboration.CreativeStepSettled;
import com.manga.agent.state.AgentStateSessionService;
import com.manga.mapper.CreativeGraphMapper;
import org.springframework.context.ApplicationEventPublisher;
import java.util.Objects;
import com.manga.agent.domain.CreativeErrorCode;
import com.manga.agent.domain.CreativeEventType;
import com.manga.agent.domain.CreativeDispatchOutboxStatus;
import com.manga.agent.domain.CreativeStepStatus;
import com.manga.agent.domain.CreativeRuntimeMessages;
import com.manga.agent.domain.SystemActor;
import com.manga.agent.event.CreativeEventSources;
import com.manga.entity.CreativeDispatchOutbox;
import com.manga.entity.CreativeStep;
import com.manga.repository.CreativeDispatchOutboxRepository;
import com.manga.repository.CreativeRunRepository;
import com.manga.repository.CreativeStepRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * 在事务内恢复过期执行租约或收敛过期取消请求。
 */
@Service
@RequiredArgsConstructor
public class CreativeStepRecoveryService {

    private final CreativeStepRepository stepRepository;
    private final CreativeRunRepository runRepository;
    private final CreativeDispatchOutboxRepository dispatchOutboxRepository;
    private final CreativeRunAggregationService aggregationService;
    private final CreativeEventService eventService;
    private final AgentStateSessionService sessions;
    private final CreativeIdentifierGenerator identifiers;
    private final CreativeGraphMapper graph;
    private final ApplicationEventPublisher publisher;

    /**
     * 运行中租约过期时以新 scheduleVersion 回到 READY 并创建新 Outbox；取消请求过期时
     * 直接收敛为 CANCELLED。返回值仅包含提交后可以安全投递的新调度信号。
     */
    @Transactional
    public Optional<CreativeDispatchSignal> recover(CreativeStep step, LocalDateTime now) {
        runRepository.lockByRunId(step.getRunId());
        String actor = SystemActor.RECONCILIATION.code();
        if (step.getStatus() == CreativeStepStatus.CANCEL_REQUESTED) {
            if (stepRepository.cancelExpired(step, now, actor)) {
                sessions.retireSession(step.getStateSessionId());
                publisher.publishEvent(new CreativeStepSettled(step.getRunId()));
                aggregationService.aggregate(step.getRunId());
            }
            return Optional.empty();
        }
        if (stepRepository.failExpiredAfterModel(step, now, CreativeErrorCode.MODEL_RESULT_UNCERTAIN.code(),
                CreativeExecutionFailure.UNCERTAIN, actor)) {
            eventService.append(step.getRunId(), step.getStepId(), CreativeEventType.STEP_FAILED,
                    CreativeEventSources.RUNTIME, null, null);
            sessions.retireSession(step.getStateSessionId());
            publisher.publishEvent(new CreativeStepSettled(step.getRunId()));
            aggregationService.aggregate(step.getRunId());
            return Optional.empty();
        }
        if (!stepRepository.recoverExpired(step, now, actor)) {
            boolean failed = stepRepository.failExpiredExhausted(step, now,
                    CreativeErrorCode.OWNER_LEASE_LOST.code(), CreativeRuntimeMessages.STEP_LEASE_RETRIES_EXHAUSTED, actor);
            if (failed) {
                eventService.append(step.getRunId(), step.getStepId(), CreativeEventType.STEP_FAILED,
                        CreativeEventSources.RUNTIME, null, null);
                sessions.retireSession(step.getStateSessionId());
                publisher.publishEvent(new CreativeStepSettled(step.getRunId()));
                aggregationService.aggregate(step.getRunId());
            }
            return Optional.empty();
        }
        if (Objects.nonNull(step.getStateSessionId())) {
            sessions.retireSession(step.getStateSessionId());
            String newSession = identifiers.nextStateSessionId();
            graph.replaceSession(step.getStepId(), newSession, actor);
            sessions.register(runRepository.findByRunId(step.getRunId()).orElseThrow(), newSession);
        }
        long nextScheduleVersion = step.getScheduleVersion() + 1L;
        dispatchOutboxRepository.create(CreativeDispatchOutbox.builder()
                .stepId(step.getStepId())
                .scheduleVersion(nextScheduleVersion)
                .status(CreativeDispatchOutboxStatus.PENDING)
                .attempts(0)
                .createdBy(actor)
                .updatedBy(actor)
                .build());
        return Optional.of(new CreativeDispatchSignal(step.getStepId(), nextScheduleVersion));
    }
}
