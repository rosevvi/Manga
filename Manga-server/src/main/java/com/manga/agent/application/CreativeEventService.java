package com.manga.agent.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.manga.agent.config.CreativeRuntimeProperties;
import com.manga.agent.domain.CreativeEventType;
import com.manga.agent.domain.CreativeOutboxStatus;
import com.manga.agent.domain.CreativeRuntimeMessages;
import com.manga.agent.domain.SystemActor;
import com.manga.agent.event.CreativeEventSources;
import com.manga.entity.CreativeEvent;
import com.manga.entity.CreativeEventOutbox;
import com.manga.entity.CreativeRun;
import com.manga.repository.CreativeEventOutboxRepository;
import com.manga.repository.CreativeEventRepository;
import com.manga.repository.CreativeRunRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 以运行级递增序号提交 Creative Event 和对应 Outbox 记录。
 */
@Service
@RequiredArgsConstructor
public class CreativeEventService {

    private final CreativeRunRepository runRepository;
    private final CreativeEventRepository eventRepository;
    private final CreativeEventOutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;
    private final CreativeRuntimeProperties runtimeProperties;

    /** 在同一事务内追加由 Creative Runtime 产生的事件。 */
    @Transactional
    public CreativeEvent append(String runId, CreativeEventType eventType, Object payload) {
        return append(runId, null, eventType, CreativeEventSources.RUNTIME, null, payload);
    }

    /** 在同一事务内分配序号、追加事件并建立可靠实时唤醒记录。 */
    @Transactional
    public CreativeEvent append(String runId, String stepId, CreativeEventType eventType,
            String source, String correlationId, Object payload) {
        CreativeRun run = runRepository.lockByRunId(runId);
        if (Objects.isNull(run)) {
            throw new IllegalArgumentException(CreativeRuntimeMessages.RUN_NOT_FOUND);
        }
        long sequence = Objects.isNull(run.getNextSequence()) ? 1L : run.getNextSequence();
        String actor = SystemActor.CREATIVE_RUNTIME.code();
        CreativeEvent event = eventRepository.create(CreativeEvent.builder()
                .runId(runId)
                .stepId(stepId)
                .sequenceNo(sequence)
                .schemaVersion(runtimeProperties.eventSchemaVersion())
                .eventType(eventType)
                .source(source)
                .correlationId(correlationId)
                .payloadJson(write(payload))
                .createdBy(actor)
                .updatedBy(actor)
                .build());
        run.setNextSequence(sequence + 1L);
        if (isTerminal(eventType)) {
            run.setTerminalSequence(sequence);
        }
        run.setUpdatedBy(actor);
        runRepository.update(run);
        outboxRepository.create(CreativeEventOutbox.builder()
                .runId(runId)
                .sequenceNo(sequence)
                .status(CreativeOutboxStatus.PENDING)
                .attempts(0)
                .createdBy(actor)
                .updatedBy(actor)
                .build());
        return event;
    }

    /** 查询指定序号之后已经提交的事件。 */
    @Transactional(readOnly = true)
    public List<CreativeEvent> findAfter(String runId, long afterSequence) {
        return eventRepository.findAfter(runId, afterSequence);
    }

    /** 将持久化事件载荷解析为 JSON 树。 */
    public JsonNode payload(CreativeEvent event) {
        try {
            return objectMapper.readTree(event.getPayloadJson());
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException(CreativeRuntimeMessages.EVENT_PAYLOAD_CORRUPTED, exception);
        }
    }

    /** 将事件载荷序列化为稳定 JSON 文本。 */
    private String write(Object payload) {
        try {
            return objectMapper.writeValueAsString(Objects.isNull(payload) ? Map.of() : payload);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException(CreativeRuntimeMessages.EVENT_PAYLOAD_SERIALIZATION_FAILED, exception);
        }
    }

    /** 判断事件是否结束 Run 的事件流。 */
    private boolean isTerminal(CreativeEventType eventType) {
        return eventType == CreativeEventType.RUN_COMPLETED
                || eventType == CreativeEventType.RUN_FAILED
                || eventType == CreativeEventType.RUN_CANCELLED;
    }
}
