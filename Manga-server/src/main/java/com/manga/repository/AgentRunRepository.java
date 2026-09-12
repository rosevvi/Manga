package com.manga.repository;

import com.manga.entity.AgentRun;
import com.manga.mapper.AgentRunMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/** 助手运行持久化入口。 */
@Repository
@RequiredArgsConstructor
public class AgentRunRepository {
    private final AgentRunMapper mapper;

    public AgentRun create(AgentRun run) {
        mapper.insert(run);
        return run;
    }

    public Optional<AgentRun> findOwned(String runId, long userId) {
        return Optional.ofNullable(mapper.findOwned(runId, userId));
    }

    public AgentRun lockByRunId(String runId) {
        return mapper.lockByRunId(runId);
    }

    public void update(AgentRun run) {
        mapper.updateById(run);
    }

    public List<AgentRun> findExpiredRunning(LocalDateTime now) {
        return mapper.findExpiredRunning(now);
    }
}
