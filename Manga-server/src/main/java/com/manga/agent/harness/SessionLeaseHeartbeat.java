package com.manga.agent.harness;

import com.manga.agent.domain.CreativeErrorCode;
import com.manga.agent.domain.CreativeRuntimeMessages;
import org.springframework.scheduling.TaskScheduler;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 使用受控调度线程池周期续租状态会话闸门，并把续租失败传播给 Agent 流。
 */
public final class SessionLeaseHeartbeat implements AutoCloseable {

    private final SessionGateLease lease;
    private final Sinks.One<Throwable> failure = Sinks.one();
    private final AtomicBoolean closed = new AtomicBoolean();
    private final ScheduledFuture<?> scheduledFuture;

    public SessionLeaseHeartbeat(
            SessionGateLease lease,
            Duration leaseDuration,
            TaskScheduler taskScheduler) {
        this.lease = Objects.requireNonNull(lease);
        Duration renewalInterval = leaseDuration.dividedBy(3L);
        this.scheduledFuture = taskScheduler.scheduleAtFixedRate(
                this::renew,
                Instant.now().plus(renewalInterval),
                renewalInterval);
    }

    /** 返回只在续租失败时发出错误的信号，用于终止正在执行的模型流。 */
    public Mono<Void> lossSignal() {
        return failure.asMono().flatMap(Mono::error);
    }

    /** 续租失败后只发布一次错误，并停止后续心跳。 */
    private void renew() {
        if (closed.get()) {
            return;
        }
        try {
            lease.renew();
        } catch (Throwable throwable) {
            AgentRuntimeException failureException = throwable instanceof AgentRuntimeException runtimeException
                    ? runtimeException
                    : new AgentRuntimeException(
                            CreativeErrorCode.STATE_STORE_UNAVAILABLE,
                            CreativeRuntimeMessages.AGENT_STATE_SESSION_GATE_UNAVAILABLE,
                            throwable);
            failure.tryEmitValue(failureException);
            close();
        }
    }

    /** 停止周期续租，可重复调用。 */
    @Override
    public void close() {
        if (closed.compareAndSet(false, true)) {
            scheduledFuture.cancel(false);
        }
    }
}
