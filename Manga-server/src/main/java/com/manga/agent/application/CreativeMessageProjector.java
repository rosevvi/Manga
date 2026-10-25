package com.manga.agent.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.manga.agent.config.CreativeMemoryProperties;
import com.manga.agent.domain.CreativeMemoryConstants;
import com.manga.agent.domain.CreativeMessageRole;
import com.manga.agent.domain.CreativeRunStatus;
import com.manga.agent.domain.CreativeRuntimeMessages;
import com.manga.agent.domain.CreativeToolStatus;
import com.manga.agent.domain.SystemActor;
import com.manga.agent.event.ContentDeltaEventPayload;
import com.manga.agent.event.RunAcceptedEventPayload;
import com.manga.agent.event.ToolExecutionEventPayload;
import com.manga.entity.CreativeEvent;
import com.manga.entity.CreativeMessage;
import com.manga.entity.CreativeRun;
import com.manga.repository.CreativeEventRepository;
import com.manga.repository.CreativeMessageRepository;
import com.manga.repository.CreativeRunRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;

/** 从追加事件生成可丢弃重建的消息；消息更新与游标推进在同一事务中提交。 */
@Service
@RequiredArgsConstructor
public class CreativeMessageProjector {
    private final CreativeRunRepository runRepository;
    private final CreativeEventRepository eventRepository;
    private final CreativeMessageRepository messageRepository;
    private final CreativeMemoryProperties properties;
    private final ObjectMapper objectMapper;

    /** 加 Run 行锁后按序推进一批事件；重复调用不会重复追加正文。 */
    @Transactional
    public int project(String runId) {
        CreativeRun run = runRepository.lockByRunId(runId);
        if (Objects.isNull(run) || Objects.isNull(run.getConversationId())) { return 0; }
        return applyBatch(run);
    }

    /** 在同一事务中清除该 Run 的派生消息，并从第一条事件完整重建。 */
    @Transactional
    public void rebuild(String runId) {
        CreativeRun run = runRepository.lockByRunId(runId);
        if (Objects.isNull(run) || Objects.isNull(run.getConversationId())) { return; }
        messageRepository.deleteByRun(runId);
        run.setProjectedThroughSequence(0L);
        runRepository.updateProjectionCursor(runId, 0L, SystemActor.CREATIVE_RUNTIME.code());
        while (applyBatch(run) > 0) { /* 每批都推进水位，直到已提交日志末尾。 */ }
    }

    /** 校验序号连续性并将一批可见消息变化与投影水位原子保存。 */
    private int applyBatch(CreativeRun run) {
        long cursor = Objects.requireNonNullElse(run.getProjectedThroughSequence(), 0L);
        List<CreativeEvent> events = eventRepository.findBatchAfter(
                run.getRunId(), cursor, properties.projectionBatchSize());
        for (CreativeEvent event : events) {
            if (event.getSequenceNo() != cursor + 1L) {
                throw new IllegalStateException(CreativeMemoryConstants.PROJECTION_GAP);
            }
            apply(run, event);
            cursor = event.getSequenceNo();
        }
        if (!events.isEmpty()) {
            runRepository.updateProjectionCursor(run.getRunId(), cursor, SystemActor.CREATIVE_RUNTIME.code());
            run.setProjectedThroughSequence(cursor);
        }
        return events.size();
    }

    /** 只投影用户可见事实，Run 终态统一收敛消息和未完成工具状态。 */
    private void apply(CreativeRun run, CreativeEvent event) {
        switch (event.getEventType()) {
            case RUN_ACCEPTED -> {
                RunAcceptedEventPayload payload = read(event, RunAcceptedEventPayload.class);
                CreativeMessage user = message(run, event, CreativeMessageRole.USER, CreativeMemoryConstants.USER_KEY);
                user.setContent(Objects.requireNonNullElse(payload.instruction(), ""));
                messageRepository.save(user);
                messageRepository.save(message(run, event, CreativeMessageRole.ASSISTANT, CreativeMemoryConstants.ASSISTANT_KEY));
            }
            case CONTENT_DELTA -> {
                CreativeMessage assistant = message(run, event, CreativeMessageRole.ASSISTANT, CreativeMemoryConstants.ASSISTANT_KEY);
                assistant.setContent(assistant.getContent() + read(event, ContentDeltaEventPayload.class).delta());
                messageRepository.save(assistant);
            }
            case TOOL_STARTED, TOOL_COMPLETED -> {
                ToolExecutionEventPayload payload = read(event, ToolExecutionEventPayload.class);
                CreativeMessage tool = message(run, event, CreativeMessageRole.TOOL,
                        CreativeMemoryConstants.TOOL_KEY_PREFIX + payload.toolCallId());
                tool.setToolCallId(payload.toolCallId());
                tool.setToolName(payload.toolName());
                tool.setToolStatus(payload.status());
                messageRepository.save(tool);
            }
            case RUN_COMPLETED, RUN_FAILED, RUN_CANCELLED -> {
                CreativeRunStatus status = switch (event.getEventType()) {
                    case RUN_COMPLETED -> CreativeRunStatus.COMPLETED;
                    case RUN_FAILED -> CreativeRunStatus.FAILED;
                    default -> CreativeRunStatus.CANCELLED;
                };
                messageRepository.finishRun(run.getRunId(), status.code(),
                        CreativeToolStatus.INTERRUPTED.name(), event.getSequenceNo(), SystemActor.CREATIVE_RUNTIME.code());
            }
            default -> { /* 调度和租约事件只推进游标，不创建展示消息。 */ }
        }
    }

    /** 使用稳定哈希投影键定位逻辑消息，首次创建时间取自事实事件。 */
    private CreativeMessage message(CreativeRun run, CreativeEvent event, CreativeMessageRole role, String logicalKey) {
        String key = projectionKey(run.getRunId(), logicalKey);
        CreativeMessage message = messageRepository.find(key).orElseGet(() -> CreativeMessage.builder()
                .conversationId(run.getConversationId()).runId(run.getRunId()).projectionKey(key)
                .role(role).status(CreativeRunStatus.ACTIVE).content("")
                .firstSequence(event.getSequenceNo()).createdAt(event.getCreatedAt())
                .createdBy(SystemActor.CREATIVE_RUNTIME.code()).build());
        message.setLastSequence(event.getSequenceNo());
        message.setUpdatedAt(event.getCreatedAt());
        message.setUpdatedBy(SystemActor.CREATIVE_RUNTIME.code());
        return message;
    }

    /** 投影键只由来源 Run 与逻辑消息决定，重放与批次划分不改变消息身份。 */
    public static String projectionKey(String runId, String logicalKey) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest((runId + ":" + logicalKey).getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(CreativeRuntimeMessages.SHA_256_UNAVAILABLE, exception);
        }
    }

    /** 按事件类型读取类型化载荷，损坏时回滚整批投影及水位。 */
    private <T> T read(CreativeEvent event, Class<T> type) {
        try { return objectMapper.readValue(event.getPayloadJson(), type); }
        catch (JsonProcessingException exception) {
            throw new IllegalStateException(CreativeMemoryConstants.PAYLOAD_INVALID, exception);
        }
    }
}
