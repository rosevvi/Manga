package com.manga.agent.application;

import com.manga.agent.config.CreativeMemoryProperties;
import com.manga.agent.config.CreativeSchedulingConstants;
import com.manga.repository.AgentStateSessionRepository;
import com.manga.repository.CreativeRunRepository;
import com.manga.agent.state.AgentStateSessionService;
import com.manga.entity.AgentStateSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import java.time.LocalDateTime;

/** 补偿未完成投影并清理超过保留期限的内部状态，逐项隔离失败。 */
@Component
@RequiredArgsConstructor
@Slf4j
public class CreativeMemoryMaintenance {
    private final CreativeRunRepository runs;
    private final CreativeMessageProjector projector;
    private final AgentStateSessionRepository sessions;
    private final AgentStateSessionService sessionService;
    private final CreativeMemoryProperties properties;

    /** 定期推进投影及清理状态，任务失败保留事实和游标以供下次继续。 */
    @Scheduled(fixedDelayString = CreativeSchedulingConstants.MEMORY_MAINTENANCE_INTERVAL)
    public void maintain() {
        for (String runId : runs.findUnprojected(null, properties.projectionBatchSize())) {
            try { projector.project(runId); }
            catch (RuntimeException exception) { log.warn("Message projection retry failed runId={}", runId, exception); }
        }
        LocalDateTime before = LocalDateTime.now().minus(properties.stateRetention());
        for (AgentStateSession session : sessions.expired(before, properties.projectionBatchSize())) {
            try { sessionService.purge(session, before); }
            catch (RuntimeException exception) { log.warn("Agent state cleanup failed runId={}", session.getRunId(), exception); }
        }
    }
}
