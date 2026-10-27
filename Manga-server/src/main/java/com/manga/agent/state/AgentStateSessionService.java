package com.manga.agent.state;

import com.manga.agent.domain.CreativeErrorCode;
import com.manga.agent.domain.CreativeMemoryConstants;
import com.manga.agent.domain.SystemActor;
import com.manga.agent.harness.AgentRuntimeException;
import com.manga.entity.AgentStateSession;
import com.manga.entity.CreativeRun;
import com.manga.repository.AgentStateSessionRepository;
import com.manga.repository.CreativeConversationRepository;
import com.manga.repository.MangaAgentStateRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Objects;

/** 管理每次 Run 独立的 StateSession，撤销与写入共用数据库行锁。 */
@Service
@RequiredArgsConstructor
public class AgentStateSessionService {
    private final AgentStateSessionRepository sessions;
    private final CreativeConversationRepository conversations;
    private final MangaAgentStateRepository states;

    /** 在受理事务中登记状态写入权，generation 由可信会话记录提供。 */
    @Transactional
    public void register(CreativeRun run, String sessionId) {
        String actor = SystemActor.CREATIVE_RUNTIME.code();
        sessions.create(AgentStateSession.builder().stateSessionId(sessionId).runId(run.getRunId())
                .userKey(Long.toString(run.getUserId())).generation(run.getStateGeneration())
                .status(AgentStateSessionStatus.ACTIVE).expiresAt(run.getDeadlineAt())
                .createdBy(actor).updatedBy(actor).build());
    }

    /** 在 StateStore 写入所在事务中锁住许可；撤销先提交后任何晚到写入都会失败。 */
    @Transactional
    public void requireWritable(String userKey, String sessionId) {
        AgentStateSession session = sessions.lock(userKey, sessionId).orElse(null);
        if (Objects.isNull(session) || session.getStatus() != AgentStateSessionStatus.ACTIVE
                || !session.getExpiresAt().isAfter(LocalDateTime.now())) {
            throw new AgentRuntimeException(CreativeErrorCode.OWNER_LEASE_LOST, CreativeMemoryConstants.SESSION_RETIRED);
        }
    }

    /** 完成后撤销写入；失败或首次取消立即推进会话代次，重复通知保持幂等。 */
    @Transactional
    public void retire(CreativeRun run, boolean invalidated) {
        String actor = SystemActor.CREATIVE_RUNTIME.code();
        sessions.retireRun(run.getRunId(), actor);
        // 审批暂停已撤销旧许可；后续取消仍必须推进 generation，CAS 保证重复终态不会再次推进。
        if (invalidated && Objects.nonNull(run.getConversationId())) {
            conversations.advanceGeneration(run.getConversationId(), run.getStateGeneration(), actor);
        }
    }

    /** 锁定过期许可后删除内部状态及登记；不删除消息和事件，未登记会话不可重建写入。 */
    @Transactional
    public void purge(AgentStateSession candidate, LocalDateTime before) {
        AgentStateSession session = sessions.lock(candidate.getUserKey(), candidate.getStateSessionId()).orElse(null);
        if (Objects.isNull(session) || !session.getExpiresAt().isBefore(before)) { return; }
        states.deleteSession(session.getUserKey(), session.getStateSessionId());
        sessions.delete(session.getStateSessionId());
    }
}
