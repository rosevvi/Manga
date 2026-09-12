package com.manga.repository;

import com.manga.entity.AgentEvent;
import com.manga.mapper.AgentEventMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;

/** 助手事件日志持久化入口。 */
@Repository
@RequiredArgsConstructor
public class AgentEventRepository {
    private final AgentEventMapper mapper;

    public AgentEvent create(AgentEvent event) {
        mapper.insert(event);
        return event;
    }

    public List<AgentEvent> findAfter(String runId, long afterSequence) {
        return mapper.findAfter(runId, afterSequence);
    }
}
