package com.manga.repository;

import com.manga.entity.AgentMessage;
import com.manga.mapper.AgentMessageMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;

/** 助手消息投影持久化入口。 */
@Repository
@RequiredArgsConstructor
public class AgentMessageRepository {
    private final AgentMessageMapper mapper;

    public AgentMessage create(AgentMessage message) {
        mapper.insert(message);
        return message;
    }

    public List<AgentMessage> findByConversation(String conversationId) {
        return mapper.findByConversation(conversationId);
    }

    public long nextOrder(String conversationId) {
        Long value = mapper.nextOrder(conversationId);
        return value == null ? 1 : value;
    }
}
