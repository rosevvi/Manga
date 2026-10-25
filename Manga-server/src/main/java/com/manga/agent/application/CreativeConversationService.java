package com.manga.agent.application;

import com.manga.agent.api.CreativeConversationHistory;
import com.manga.agent.api.CreativeConversationView;
import com.manga.agent.api.CreativeMessageView;
import com.manga.agent.api.CreativeRuntimeResponseCode;
import com.manga.agent.config.CreativeMemoryProperties;
import com.manga.agent.domain.CreativeMemoryConstants;
import com.manga.agent.domain.SystemActor;
import com.manga.common.enums.CommonResponseCode;
import com.manga.common.exception.BusinessException;
import com.manga.entity.CreativeConversation;
import com.manga.entity.CreativeMessage;
import com.manga.repository.CreativeConversationRepository;
import com.manga.repository.CreativeMessageRepository;
import com.manga.repository.CreativeRunRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.Collections;
import java.util.List;
import java.util.ArrayList;
import java.util.Objects;

/** 提供会话历史的所有权边界、分页和按事件重建能力。 */
@Service
@RequiredArgsConstructor
public class CreativeConversationService {
    private final CreativeConversationRepository conversations;
    private final CreativeMessageRepository messages;
    private final CreativeRunRepository runs;
    private final CreativeMessageProjector projector;
    private final CreativeMemoryProperties properties;

    /** 按创建主键倒序返回用户会话；分页不因会话更新时间变化而跳过记录。 */
    @Transactional(readOnly = true)
    public List<CreativeConversationView> list(long userId, Long projectId, Long beforeId) {
        return conversations.listOwned(userId, projectId, beforeId, properties.historyPageSize())
                .stream().map(this::view).toList();
    }

    /** 返回一致的已投影消息和活动运行 cursor；SSE 只负责提示继续读取。 */
    @Transactional
    public CreativeConversationHistory history(long userId, String conversationId, Long beforeId) {
        CreativeConversation conversation = requireOwned(userId, conversationId);
        for (String runId : runs.findUnprojected(conversationId, properties.projectionBatchSize())) {
            while (projector.project(runId) > 0) { /* 从事务水位恢复遗漏投影。 */ }
        }
        List<CreativeMessage> page = new ArrayList<>(messages.history(
                conversationId, beforeId, properties.historyPageSize() + 1));
        boolean hasMore = page.size() > properties.historyPageSize();
        if (hasMore) { page.removeLast(); }
        Collections.reverse(page);
        Long nextBeforeId = hasMore ? page.getFirst().getId() : null;
        CreativeConversationHistory.ActiveRun active = runs.findActiveConversation(conversationId)
                .map(run -> new CreativeConversationHistory.ActiveRun(
                        run.getRunId(), run.getStatus(), Objects.requireNonNullElse(run.getProjectedThroughSequence(), 0L)))
                .orElse(null);
        return new CreativeConversationHistory(view(conversation), page.stream().map(this::view).toList(), nextBeforeId, active);
    }

    /** 在无活动 Run 时事务内重建全部消息并清空旧摘要水位，后续上下文会重新压缩。 */
    @Transactional
    public void rebuild(long userId, String conversationId) {
        CreativeConversation conversation = conversations.lockOwned(conversationId, userId)
                .orElseThrow(this::unavailable);
        if (runs.findActiveConversation(conversationId).isPresent()) {
            throw new BusinessException(CreativeRuntimeResponseCode.ACTIVE_RUN_CONFLICT, HttpStatus.CONFLICT);
        }
        for (String runId : runs.findConversationRunIds(conversationId)) { projector.rebuild(runId); }
        conversation.setMemorySummary("");
        conversation.setSummarizedThroughMessageId(0L);
        conversation.setUpdatedBy(SystemActor.CREATIVE_RUNTIME.code());
        conversations.update(conversation);
    }

    /** 所有历史查询都从认证用户校验会话归属。 */
    private CreativeConversation requireOwned(long userId, String conversationId) {
        return conversations.findOwned(conversationId, userId).orElseThrow(this::unavailable);
    }

    /** 返回统一拒绝消息，避免泄露其他用户会话是否存在。 */
    private BusinessException unavailable() {
        return new BusinessException(CommonResponseCode.FORBIDDEN,
                CreativeMemoryConstants.CONVERSATION_UNAVAILABLE, HttpStatus.FORBIDDEN);
    }

    /** 投影会话列表字段，不暴露内部上下文摘要。 */
    private CreativeConversationView view(CreativeConversation conversation) {
        return new CreativeConversationView(conversation.getId(), conversation.getConversationId(),
                conversation.getTitle(), conversation.getProjectId(), conversation.getStateGeneration(), conversation.getUpdatedAt());
    }

    /** 投影用户可见的消息字段。 */
    private CreativeMessageView view(CreativeMessage message) {
        return new CreativeMessageView(message.getId(), message.getProjectionKey(), message.getRunId(),
                message.getRole(), message.getStatus(), message.getContent(),
                message.getToolName(), message.getToolStatus(), message.getCreatedAt());
    }
}
