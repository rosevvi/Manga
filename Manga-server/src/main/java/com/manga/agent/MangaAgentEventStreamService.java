package com.manga.agent;

import com.manga.dto.AgentEventResponse;
import com.manga.entity.AgentEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;

/** 从持久化日志回放后连接实时唤醒信号的 SSE 读取服务。 */
@Service
@RequiredArgsConstructor
public class MangaAgentEventStreamService {

    private final MangaAgentRuntimeService runtimeService;
    private final AgentEventService eventService;
    private final AgentEventSignalHub signalHub;

    public Flux<AgentEventResponse> stream(String runId, long userId, long afterSequence) {
        runtimeService.findRun(runId, userId);
        AtomicLong cursor = new AtomicLong(Math.max(0, afterSequence));
        Flux<AgentEventResponse> replay = readAfter(runId, cursor);
        Flux<AgentEventResponse> live = Flux.merge(
                        signalHub.listen(runId),
                        Flux.interval(Duration.ofSeconds(2)).map(ignored -> runId))
                .concatMap(ignored -> readAfter(runId, cursor));
        return Flux.concat(replay, live).takeUntil(this::isTerminal);
    }

    private Flux<AgentEventResponse> readAfter(String runId, AtomicLong cursor) {
        return Flux.fromIterable(eventService.findAfter(runId, cursor.get()))
                .map(event -> toResponse(event, cursor));
    }

    private AgentEventResponse toResponse(AgentEvent event, AtomicLong cursor) {
        cursor.set(event.getSequenceNo());
        return new AgentEventResponse(event.getSequenceNo(), event.getEventType(), eventService.payload(event),
                event.getCreatedAt());
    }

    private boolean isTerminal(AgentEventResponse event) {
        return AgentEventType.RUN_COMPLETED.name().equals(event.eventType())
                || AgentEventType.RUN_FAILED.name().equals(event.eventType())
                || AgentEventType.RUN_CANCELLED.name().equals(event.eventType());
    }
}
