package com.manga.agent.event;

import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 合并 Redis 的有损 sequence 提示，只把它作为查库触发器，避免慢查询期间无限堆积唤醒信号。
 */
public final class CreativeEventWakeGate {

    private final AtomicBoolean dirty = new AtomicBoolean();
    private final AtomicLong highestHint = new AtomicLong();
    private final AtomicReference<Sinks.One<Long>> waiter = new AtomicReference<>();

    /** 记录当前观察到的最大事件序号，并唤醒至多一个等待中的数据库回放周期。 */
    public void markDirty(long sequenceHint) {
        if (sequenceHint <= 0) {
            throw new IllegalArgumentException("sequenceHint must be positive");
        }
        highestHint.accumulateAndGet(sequenceHint, Math::max);
        dirty.set(true);
        Sinks.One<Long> waiting = waiter.getAndSet(null);
        if (waiting != null) {
            waiting.tryEmitValue(sequenceHint);
        }
    }

    /** Redis 流结束或异常时主动触发一次查库，此后由补偿轮询继续推进。 */
    public void requestPoll() {
        dirty.set(true);
        Sinks.One<Long> waiting = waiter.getAndSet(null);
        if (waiting != null) {
            waiting.tryEmitValue(highestHint.get());
        }
    }

    /** 等待合并后的 Redis 提示；没有提示时在补偿周期到达后返回。 */
    public Mono<Long> awaitDirtyOrPoll(Duration pollInterval) {
        Duration safeInterval = Objects.requireNonNull(pollInterval, "pollInterval must not be null");
        if (safeInterval.isZero() || safeInterval.isNegative()) {
            return Mono.error(new IllegalArgumentException("pollInterval must be positive"));
        }
        return Mono.defer(() -> {
            Long ready = consumeDirty();
            if (ready != null) {
                return Mono.just(ready);
            }
            Sinks.One<Long> installed = Sinks.one();
            if (!waiter.compareAndSet(null, installed)) {
                return Mono.error(new IllegalStateException("Only one Creative event replay waiter may be active"));
            }
            ready = consumeDirty();
            if (ready != null) {
                waiter.compareAndSet(installed, null);
                installed.tryEmitEmpty();
                return Mono.just(ready);
            }
            return Mono.firstWithSignal(
                            installed.asMono(),
                            Mono.delay(safeInterval).thenReturn(0L))
                    .map(this::consumeAfterWake)
                    .doFinally(ignored -> waiter.compareAndSet(installed, null));
        });
    }

    private Long consumeDirty() {
        return dirty.getAndSet(false) ? highestHint.get() : null;
    }

    private long consumeAfterWake(long sequenceHint) {
        dirty.set(false);
        return sequenceHint;
    }
}
