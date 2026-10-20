package com.manga.agent.application;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.manga.agent.domain.CreativeEventType;
import com.manga.agent.api.CreativeRunResponse;
import com.manga.agent.domain.CreativeRunStatus;
import com.manga.agent.event.CreativeEventRedisSignalService;
import com.manga.agent.event.CreativeEventSources;
import com.manga.entity.CreativeEvent;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import reactor.core.scheduler.Schedulers;
import reactor.test.StepVerifier;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 验证 Creative Event 从数据库游标回放、实时唤醒补查和终态关闭。
 */
class CreativeEventStreamServiceTests {

    private static final String RUN_ID = "run-test";
    private static final long USER_ID = 101L;

    @Test
    void shouldReplayAfterCursorAndCloseOnTerminalEvent() {
        CreativeEventService eventService = mock(CreativeEventService.class);
        CreativeEventRedisSignalService signals = signals(Flux.never());
        CreativeEventStreamService service = service(eventService, signals);
        CreativeEvent content = event(3L, CreativeEventType.CONTENT_DELTA);
        CreativeEvent completed = event(4L, CreativeEventType.RUN_COMPLETED);
        when(eventService.findAfter(RUN_ID, 2L)).thenReturn(List.of(content, completed));
        when(eventService.payload(content)).thenReturn(JsonNodeFactory.instance.objectNode().put("delta", "完成"));
        when(eventService.payload(completed)).thenReturn(JsonNodeFactory.instance.objectNode());

        StepVerifier.create(service.stream(RUN_ID, USER_ID, 2L))
                .assertNext(envelope -> {
                    assertThat(envelope.sequence()).isEqualTo(3L);
                    assertThat(envelope.eventType()).isEqualTo(CreativeEventType.CONTENT_DELTA);
                })
                .assertNext(envelope -> {
                    assertThat(envelope.sequence()).isEqualTo(4L);
                    assertThat(envelope.eventType()).isEqualTo(CreativeEventType.RUN_COMPLETED);
                })
                .verifyComplete();
    }

    @Test
    void shouldReadCommittedEventAfterWakeUpSignal() {
        CreativeEventService eventService = mock(CreativeEventService.class);
        Sinks.Many<Long> wakeups = Sinks.many().unicast().onBackpressureBuffer();
        CreativeEventStreamService service = service(eventService, signals(wakeups.asFlux()));
        CreativeEvent cancelled = event(1L, CreativeEventType.RUN_CANCELLED);
        when(eventService.findAfter(RUN_ID, 0L)).thenReturn(List.of(), List.of(cancelled));
        when(eventService.payload(cancelled)).thenReturn(JsonNodeFactory.instance.objectNode());

        StepVerifier.create(service.stream(RUN_ID, USER_ID, 0L))
                .then(() -> wakeups.tryEmitNext(1L))
                .assertNext(envelope -> assertThat(envelope.eventType())
                        .isEqualTo(CreativeEventType.RUN_CANCELLED))
                .verifyComplete();
    }

    @Test
    void shouldPollMysqlWhenRedisSubscriptionIsUnavailable() {
        CreativeEventService eventService = mock(CreativeEventService.class);
        CreativeEventRedisSignalService signals = mock(CreativeEventRedisSignalService.class);
        CreativeEvent cancelled = event(1L, CreativeEventType.RUN_CANCELLED);
        when(signals.wakeupsWhenSubscribed(RUN_ID))
                .thenReturn(Mono.error(new IllegalStateException("redis unavailable")));
        when(eventService.findAfter(RUN_ID, 0L)).thenReturn(List.of(), List.of(cancelled));
        when(eventService.payload(cancelled)).thenReturn(JsonNodeFactory.instance.objectNode());
        CreativeEventStreamService service = service(eventService, signals);

        StepVerifier.withVirtualTime(() -> service.stream(RUN_ID, USER_ID, 0L))
                .thenAwait(java.time.Duration.ofMillis(301))
                .assertNext(envelope -> assertThat(envelope.eventType())
                        .isEqualTo(CreativeEventType.RUN_CANCELLED))
                .verifyComplete();
    }

    @Test
    void shouldCloseReconnectWhenCursorAlreadyReachedTerminalEvent() {
        CreativeEventService eventService = mock(CreativeEventService.class);
        CreativeRunQueryService queryService = mock(CreativeRunQueryService.class);
        CreativeRunResponse run = mock(CreativeRunResponse.class);
        when(run.status()).thenReturn(CreativeRunStatus.COMPLETED);
        when(queryService.findOwned(RUN_ID, USER_ID)).thenReturn(run);
        when(eventService.findAfter(RUN_ID, 4L)).thenReturn(List.of());
        CreativeEventStreamService service = new CreativeEventStreamService(
                queryService, eventService, signals(Flux.never()), Schedulers.immediate());

        StepVerifier.create(service.stream(RUN_ID, USER_ID, 4L)).verifyComplete();
    }

    /** 创建使用立即调度器的测试服务，避免测试依赖真实线程池。 */
    private CreativeEventStreamService service(CreativeEventService eventService,
            CreativeEventRedisSignalService signals) {
        CreativeRunQueryService queryService = mock(CreativeRunQueryService.class);
        return new CreativeEventStreamService(queryService, eventService, signals, Schedulers.immediate());
    }

    /** 创建在订阅确认后返回指定唤醒流的 Redis 信号服务。 */
    private CreativeEventRedisSignalService signals(Flux<Long> wakeups) {
        CreativeEventRedisSignalService signals = mock(CreativeEventRedisSignalService.class);
        when(signals.wakeupsWhenSubscribed(RUN_ID)).thenReturn(Mono.just(wakeups));
        return signals;
    }

    /** 创建一条最小的已提交 Creative Event 记录。 */
    private CreativeEvent event(long sequence, CreativeEventType eventType) {
        return CreativeEvent.builder()
                .runId(RUN_ID)
                .sequenceNo(sequence)
                .schemaVersion(1)
                .eventType(eventType)
                .source(CreativeEventSources.RUNTIME)
                .payloadJson("{}")
                .createdAt(LocalDateTime.now())
                .build();
    }
}
