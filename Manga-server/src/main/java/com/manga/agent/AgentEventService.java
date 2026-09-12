package com.manga.agent;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.manga.entity.AgentEvent;
import com.manga.entity.AgentEventOutbox;
import com.manga.entity.AgentRun;
import com.manga.repository.AgentEventOutboxRepository;
import com.manga.repository.AgentEventRepository;
import com.manga.repository.AgentRunRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** 以运行级递增序号提交事件日志和可靠投递记录。 */
@Service
@RequiredArgsConstructor
public class AgentEventService {

    private final AgentRunRepository runRepository;
    private final AgentEventRepository eventRepository;
    private final AgentEventOutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;

    @Transactional
    public AgentEvent append(String runId, AgentEventType eventType, Object payload) {
        AgentRun run = runRepository.lockByRunId(runId);
        if (run == null) {
            throw new IllegalArgumentException("助手运行不存在");
        }
        long sequence = run.getNextSequence() == null ? 1 : run.getNextSequence();
        AgentEvent event = eventRepository.create(AgentEvent.builder()
                .runId(runId)
                .sequenceNo(sequence)
                .eventType(eventType.name())
                .payloadJson(write(payload))
                .build());
        run.setNextSequence(sequence + 1);
        runRepository.update(run);
        outboxRepository.create(AgentEventOutbox.builder()
                .runId(runId)
                .sequenceNo(sequence)
                .status("PENDING")
                .attempts(0)
                .build());
        return event;
    }

    public List<AgentEvent> findAfter(String runId, long afterSequence) {
        return eventRepository.findAfter(runId, afterSequence);
    }

    public JsonNode payload(AgentEvent event) {
        try {
            return objectMapper.readTree(event.getPayloadJson());
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("助手事件载荷损坏", exception);
        }
    }

    private String write(Object payload) {
        try {
            return objectMapper.writeValueAsString(payload == null ? java.util.Map.of() : payload);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("助手事件载荷序列化失败", exception);
        }
    }
}
