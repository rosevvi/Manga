package com.manga.agent.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.manga.agent.config.CreativeRuntimeProperties;
import com.manga.agent.domain.CreativeEventType;
import com.manga.agent.domain.CreativeRuntimeMessages;
import com.manga.agent.domain.SystemActor;
import com.manga.agent.event.CreativeEventRedisSignalService;
import com.manga.agent.event.CreativeEventSources;
import com.manga.agent.state.AgentStateSessionService;
import com.manga.entity.CreativeEvent;
import com.manga.entity.CreativeRun;
import com.manga.repository.CreativeEventRepository;
import com.manga.repository.CreativeRunRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 以运行级递增序号提交 Creative Event，并在事务提交后发布 Redis 唤醒提示。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CreativeEventService {

    private final CreativeRunRepository runRepository;
    private final CreativeEventRepository eventRepository;
    private final CreativeEventRedisSignalService redisSignalService;
    private final ObjectMapper objectMapper;
    private final CreativeRuntimeProperties runtimeProperties;
    private final CreativeMessageProjector messageProjector;
    private final AgentStateSessionService stateSessions;

    /** 在同一事务内追加由 Creative Runtime 产生的事件。 */
    @Transactional
    public CreativeEvent append(String runId, CreativeEventType eventType, Object payload) {
        return append(runId, null, eventType, CreativeEventSources.RUNTIME, null, payload);
    }

    /** 在同一事务内分配序号、追加事件，并注册提交后的实时唤醒。 */
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
        // 写入事实后同步推进可重建投影；批次游标和消息使用同一事务提交。
        messageProjector.project(runId);
        if (eventType == CreativeEventType.RUN_CANCEL_REQUESTED || isTerminal(eventType)) {
            stateSessions.retire(run, eventType != CreativeEventType.RUN_COMPLETED);
        }
        publishAfterCommit(runId, sequence);
        return event;
    }

    /**
     * 只在数据库事务成功提交后发布。Redis 发布失败不会回滚事实数据，SSE 会通过 MySQL 补偿查询恢复。
     */
    private void publishAfterCommit(String runId, long sequence) {
        Runnable publish = () -> redisSignalService.publishWakeup(runId, sequence)
                .subscribe(
                        ignored -> { },
                        failure -> log.debug(
                                "Creative event Redis wake-up unavailable; MySQL polling will recover runId={} sequence={} type={}",
                                runId, sequence, failure.getClass().getSimpleName()));
        if (TransactionSynchronizationManager.isActualTransactionActive()
                && TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    publish.run();
                }
            });
            return;
        }
        publish.run();
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
