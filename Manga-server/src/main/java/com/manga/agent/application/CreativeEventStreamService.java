package com.manga.agent.application;

import com.manga.agent.domain.CreativeEventType;
import com.manga.agent.api.CreativeRunResponse;
import com.manga.agent.event.CreativeEventEnvelope;
import com.manga.agent.event.CreativeEventRedisSignalService;
import com.manga.agent.event.CreativeEventWakeGate;
import com.manga.entity.CreativeEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;
import java.util.Objects;

/**
 * 将持久化事件先回放再连接实时唤醒，并输出 Creative Event Envelope。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CreativeEventStreamService {

    private static final Duration REPAIR_POLL_INTERVAL = Duration.ofMillis(300);

    private final CreativeRunQueryService runQueryService;
    private final CreativeEventService eventService;
    private final CreativeEventRedisSignalService redisSignalService;
    private final Scheduler mangaBlockingScheduler;

    /** 校验 Run 所有权后，从指定事件序号继续回放并实时推送至终态。 */
    public Flux<CreativeEventEnvelope> stream(String runId, long userId, long afterSequence) {
        CreativeRunResponse run = runQueryService.findOwned(runId, userId);
        long safeAfterSequence = Math.max(0, afterSequence);
        if (Objects.nonNull(run) && run.status().isTerminal()) {
            return readAfter(runId, new AtomicLong(safeAfterSequence)).takeUntil(this::isTerminal);
        }
        return Flux.defer(() -> redisSignalService.wakeupsWhenSubscribed(runId)
                .onErrorResume(failure -> {
                    log.debug("Creative event Redis subscription unavailable; polling MySQL runId={} type={}",
                            runId, failure.getClass().getSimpleName());
                    return Mono.just(Flux.never());
                })
                .flatMapMany(wakeups -> replayThenLive(runId, safeAfterSequence, wakeups)));
    }

    /**
     * 先订阅 Redis，再执行数据库回放；回放期间到达的唤醒会保留最新 sequence，随后触发补查。
     */
    private Flux<CreativeEventEnvelope> replayThenLive(String runId, long afterSequence,
            Flux<Long> wakeups) {
        AtomicLong cursor = new AtomicLong(afterSequence);
        CreativeEventWakeGate gate = new CreativeEventWakeGate();
        return Flux.using(
                () -> subscribeWakeups(runId, wakeups, gate),
                ignored -> replayCycle(runId, cursor, gate).takeUntil(this::isTerminal),
                Disposable::dispose);
    }

    /** 每轮只执行一次游标查库，然后等待合并后的 Redis 提示或补偿周期。 */
    private Flux<CreativeEventEnvelope> replayCycle(String runId, AtomicLong cursor,
            CreativeEventWakeGate gate) {
        return readAfter(runId, cursor)
                .concatWith(Flux.defer(() -> gate.awaitDirtyOrPoll(REPAIR_POLL_INTERVAL)
                        .flatMapMany(ignored -> replayCycle(runId, cursor, gate))));
    }

    /** 立即建立每个 Run 独立的 Redis 订阅，并在异常时退化到数据库补偿轮询。 */
    private Disposable subscribeWakeups(String runId, Flux<Long> wakeups, CreativeEventWakeGate gate) {
        return wakeups.subscribe(
                gate::markDirty,
                failure -> {
                    log.debug("Creative event Redis listener ended; polling MySQL runId={} type={}",
                            runId, failure.getClass().getSimpleName());
                    gate.requestPoll();
                },
                gate::requestPoll);
    }

    /** 在受控阻塞 Scheduler 上读取指定游标后的数据库事件。 */
    private Flux<CreativeEventEnvelope> readAfter(String runId, AtomicLong cursor) {
        return Mono.fromCallable(() -> eventService.findAfter(runId, cursor.get()))
                .subscribeOn(mangaBlockingScheduler)
                .flatMapMany(Flux::fromIterable)
                .map(event -> toEnvelope(event, cursor));
    }

    /** 将持久化事件转换为版本化的 Creative Event Envelope。 */
    private CreativeEventEnvelope toEnvelope(CreativeEvent event, AtomicLong cursor) {
        cursor.set(event.getSequenceNo());
        return new CreativeEventEnvelope(
                event.getSchemaVersion(),
                event.getRunId(),
                event.getStepId(),
                event.getSequenceNo(),
                event.getEventType(),
                event.getSource(),
                event.getCorrelationId(),
                eventService.payload(event),
                event.getCreatedAt()
        );
    }

    /** 判断事件是否为关闭 SSE 的 Run 终态事件。 */
    private boolean isTerminal(CreativeEventEnvelope event) {
        return event.eventType() == CreativeEventType.RUN_COMPLETED
                || event.eventType() == CreativeEventType.RUN_FAILED
                || event.eventType() == CreativeEventType.RUN_CANCELLED;
    }

}
