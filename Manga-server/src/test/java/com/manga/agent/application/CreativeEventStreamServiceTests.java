package com.manga.agent.application;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.manga.agent.domain.CreativeEventType;
import com.manga.agent.event.CreativeEventSignalHub;
import com.manga.agent.event.CreativeEventSources;
import com.manga.entity.CreativeEvent;
import org.junit.jupiter.api.Test;
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
        CreativeEventSignalHub signalHub = new CreativeEventSignalHub();
        CreativeEventStreamService service = service(eventService, signalHub);
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
        CreativeEventSignalHub signalHub = new CreativeEventSignalHub();
        CreativeEventStreamService service = service(eventService, signalHub);
        CreativeEvent cancelled = event(1L, CreativeEventType.RUN_CANCELLED);
        when(eventService.findAfter(RUN_ID, 0L)).thenReturn(List.of(), List.of(cancelled));
        when(eventService.payload(cancelled)).thenReturn(JsonNodeFactory.instance.objectNode());

        StepVerifier.create(service.stream(RUN_ID, USER_ID, 0L))
                .then(() -> signalHub.signal(RUN_ID))
                .assertNext(envelope -> assertThat(envelope.eventType())
                        .isEqualTo(CreativeEventType.RUN_CANCELLED))
                .verifyComplete();
    }

    /** 创建使用立即调度器的测试服务，避免测试依赖真实线程池。 */
    private CreativeEventStreamService service(CreativeEventService eventService, CreativeEventSignalHub signalHub) {
        CreativeRunQueryService queryService = mock(CreativeRunQueryService.class);
        return new CreativeEventStreamService(queryService, eventService, signalHub, Schedulers.immediate());
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
