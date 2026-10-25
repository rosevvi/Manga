package com.manga.agent.execution;

import com.manga.agent.domain.CreativeErrorCode;
import com.manga.agent.domain.CreativeRuntimeMessages;
import com.manga.agent.harness.AgentRuntimeException;
import org.springframework.scheduling.TaskScheduler;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 周期续签数据库 Step owner lease，并在执行权丢失时终止 Agent 流。
 */
public final class CreativeStepLeaseHeartbeat implements AutoCloseable {

    private final ClaimedCreativeStep step;
    private final CreativeStepLifecycleService lifecycleService;
    private final Sinks.One<Throwable> failure = Sinks.one();
    private final AtomicBoolean closed = new AtomicBoolean();
    private final ScheduledFuture<?> scheduledFuture;

    public CreativeStepLeaseHeartbeat(
            ClaimedCreativeStep step,
            CreativeStepLifecycleService lifecycleService,
            Duration leaseDuration,
            TaskScheduler taskScheduler) {
        this.step = Objects.requireNonNull(step);
        this.lifecycleService = Objects.requireNonNull(lifecycleService);
        if (Objects.isNull(leaseDuration) || leaseDuration.isZero() || leaseDuration.isNegative()) {
            throw new IllegalArgumentException(CreativeRuntimeMessages.AGENT_STEP_LEASE_DURATION_INVALID);
        }
        Duration renewalInterval = leaseDuration.dividedBy(3L);
        if (renewalInterval.isZero()) {
            renewalInterval = leaseDuration;
        }
        scheduledFuture = taskScheduler.scheduleAtFixedRate(
                this::renew,
                Instant.now().plus(renewalInterval),
                renewalInterval);
    }

    /** 返回仅在 owner lease 丢失时发出错误的终止信号。 */
    public Mono<Void> lossSignal() {
        return failure.asMono().flatMap(Mono::error);
    }

    /** 续租失败后发布一次稳定错误并停止心跳。 */
    private void renew() {
        if (closed.get()) {
            return;
        }
        try {
            if (!lifecycleService.renewLease(step)) {
                failure.tryEmitValue(renewalFailure());
                close();
            }
        } catch (Throwable throwable) {
            failure.tryEmitValue(new AgentRuntimeException(
                    CreativeErrorCode.OWNER_LEASE_LOST,
                    CreativeRuntimeMessages.AGENT_STEP_OWNER_LEASE_LOST,
                    throwable));
            close();
        }
    }

    /** 区分运行期限到达与 owner lease 被其他实例夺取。 */
    private AgentRuntimeException renewalFailure() {
        if (Objects.nonNull(step.deadlineAt()) && !step.deadlineAt().isAfter(LocalDateTime.now())) {
            return new AgentRuntimeException(
                    CreativeErrorCode.DEADLINE_EXCEEDED,
                    CreativeRuntimeMessages.AGENT_STEP_DEADLINE_EXCEEDED);
        }
        return new AgentRuntimeException(
                CreativeErrorCode.OWNER_LEASE_LOST,
                CreativeRuntimeMessages.AGENT_STEP_OWNER_LEASE_LOST);
    }

    /** 停止续租任务，可重复调用。 */
    @Override
    public void close() {
        if (closed.compareAndSet(false, true)) {
            scheduledFuture.cancel(false);
        }
    }
}
