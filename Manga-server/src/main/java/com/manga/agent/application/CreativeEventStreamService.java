package com.manga.agent.application;

import com.manga.agent.domain.CreativeEventType;
import com.manga.agent.event.CreativeEventEnvelope;
import com.manga.agent.event.CreativeEventSignalHub;
import com.manga.entity.CreativeEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 将持久化事件先回放再连接实时唤醒，并输出 Creative Event Envelope。
 */
@Service
@RequiredArgsConstructor
public class CreativeEventStreamService {

    private static final Duration REPAIR_POLL_INTERVAL = Duration.ofSeconds(2);

    private final CreativeRunQueryService runQueryService;
    private final CreativeEventService eventService;
    private final CreativeEventSignalHub signalHub;
    private final Scheduler mangaBlockingScheduler;

    /** 校验 Run 所有权后，从指定事件序号继续回放并实时推送至终态。 */
    public Flux<CreativeEventEnvelope> stream(String runId, long userId, long afterSequence) {
        runQueryService.findOwned(runId, userId);
        AtomicLong cursor = new AtomicLong(Math.max(0, afterSequence));
        Flux<CreativeEventEnvelope> replay = readAfter(runId, cursor);
        Flux<CreativeEventEnvelope> live = Flux.merge(
                        signalHub.listen(runId),
                        Flux.interval(REPAIR_POLL_INTERVAL).map(ignored -> runId))
                .concatMap(ignored -> readAfter(runId, cursor));
        return Flux.concat(replay, live).takeUntil(event -> event.eventType() == CreativeEventType.RUN_COMPLETED
                || event.eventType() == CreativeEventType.RUN_FAILED
                || event.eventType() == CreativeEventType.RUN_CANCELLED);
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

}
