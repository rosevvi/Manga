package com.manga.repository;

import com.manga.entity.AgentConversation;
import com.manga.mapper.AgentConversationMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/** 助手会话持久化入口。 */
@Repository
@RequiredArgsConstructor
public class AgentConversationRepository {
    private final AgentConversationMapper mapper;

    public Optional<AgentConversation> findOwned(String conversationId, long userId) {
        return Optional.ofNullable(mapper.findOwned(conversationId, userId));
    }

    public List<AgentConversation> findByUser(long userId, Long projectId) {
        return mapper.findByUser(userId, projectId);
    }

    public AgentConversation create(AgentConversation conversation) {
        mapper.insert(conversation);
        return conversation;
    }

    public void update(AgentConversation conversation) {
        mapper.updateById(conversation);
    }
}
