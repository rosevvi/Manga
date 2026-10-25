package com.manga.agent.application;

import com.manga.agent.config.CreativeMemoryProperties;
import com.manga.agent.domain.CreativeMemoryConstants;
import com.manga.agent.domain.SystemActor;
import com.manga.entity.CreativeConversation;
import com.manga.entity.CreativeMessage;
import com.manga.repository.CreativeConversationRepository;
import com.manga.repository.CreativeMessageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 从已成功完成的消息构造有界上下文。提取式 Compaction 保留早期摘要和近期消息，
 * 不复用失败、取消 Run 的 AgentState；完整历史始终保留在消息和事件中。
 */
@Service
@RequiredArgsConstructor
public class CreativeConversationMemory {
    private final CreativeMemoryProperties properties;
    private final CreativeMessageRepository messages;
    private final CreativeConversationRepository conversations;

    /** 调用方持有会话行锁；逐批压缩历史并保存摘要及消息水位，再冻结为本轮输入。 */
    public String compose(CreativeConversation conversation, String instruction) {
        if (Objects.isNull(conversation)) { return instruction; }
        if (properties.summaryMaxChars() >= properties.contextMaxChars() || properties.stateRetention().isNegative()) {
            throw new IllegalArgumentException(CreativeMemoryConstants.INVALID_BUDGET);
        }
        List<CreativeMessage> recent = new ArrayList<>();
        String summary = Objects.requireNonNullElse(conversation.getMemorySummary(), "");
        long watermark = Objects.requireNonNullElse(conversation.getSummarizedThroughMessageId(), 0L);
        long readThrough = watermark;
        boolean compacted = false;
        while (true) {
            List<CreativeMessage> page = messages.completedAfter(
                    conversation.getConversationId(), readThrough, properties.historyPageSize());
            if (page.isEmpty()) { break; }
            recent.addAll(page);
            readThrough = page.getLast().getId();
            while (recent.size() > properties.recentMessages()
                    || chars(recent) + summary.length() > properties.contextMaxChars()) {
                CreativeMessage oldest = recent.removeFirst();
                summary = compact(summary, oldest);
                watermark = oldest.getId();
                compacted = true;
            }
        }
        if (compacted) {
            conversation.setMemorySummary(summary);
            conversation.setSummarizedThroughMessageId(watermark);
            conversation.setUpdatedBy(SystemActor.CREATIVE_RUNTIME.code());
            conversations.update(conversation);
        }
        String history = summary + "\n" + String.join("\n", recent.stream().map(this::render).toList());
        return history.isBlank() ? instruction : CreativeMemoryConstants.HISTORY_TEMPLATE.formatted(
                excerpt(history, properties.contextMaxChars()), instruction);
    }

    /** 估算完整历史文本的字符占用，作为与模型 token 窗口分开的确定性预算。 */
    private int chars(List<CreativeMessage> recent) {
        return recent.stream().mapToInt(message -> render(message).length() + 1).sum();
    }

    /** 逐条提取旧消息要点；保留摘要开头的早期信息和尾部的最近信息。 */
    private String compact(String summary, CreativeMessage message) {
        int excerptLimit = Math.max(1, properties.summaryMaxChars() / properties.recentMessages());
        return excerpt(summary + "\n" + message.getRole().name() + ": "
                + excerpt(message.getContent(), excerptLimit), properties.summaryMaxChars());
    }

    /** 用角色边界组织完整近期消息。 */
    private String render(CreativeMessage message) {
        return message.getRole().name() + ": " + message.getContent();
    }

    /** 截取首尾两段，避免单纯截断丢失最近约定，同时保证硬预算。 */
    private String excerpt(String text, int limit) {
        if (text.length() <= limit) { return text; }
        if (limit <= CreativeMemoryConstants.EXCERPT_MARKER.length()) { return text.substring(0, limit); }
        int available = limit - CreativeMemoryConstants.EXCERPT_MARKER.length();
        int head = available / 2;
        return text.substring(0, head) + CreativeMemoryConstants.EXCERPT_MARKER
                + text.substring(text.length() - (available - head));
    }
}
