package com.manga.repository;

import com.manga.entity.AgentEventOutbox;
import com.manga.mapper.AgentEventOutboxMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;

/** 助手事件出站投递持久化入口。 */
@Repository
@RequiredArgsConstructor
public class AgentEventOutboxRepository {
    private final AgentEventOutboxMapper mapper;

    public AgentEventOutbox create(AgentEventOutbox outbox) {
        mapper.insert(outbox);
        return outbox;
    }

    public List<AgentEventOutbox> findPending(int limit) {
        return mapper.findPending(limit);
    }

    public void update(AgentEventOutbox outbox) {
        mapper.updateById(outbox);
    }
}
