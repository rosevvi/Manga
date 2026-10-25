package com.manga.repository;

import com.manga.entity.AgentStateSession;
import com.manga.mapper.AgentStateSessionMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
/** 封装 Agent 会话写入许可与清理坐标。 */
@Repository
@RequiredArgsConstructor
public class AgentStateSessionRepository {
    private final AgentStateSessionMapper mapper;
    /** 注册本次 Run 独占的状态命名空间。 */
    public void create(AgentStateSession session) {
        mapper.insert(session);
    }
    /** 持有状态会话行锁直到调用方事务完成。 */
    public Optional<AgentStateSession> lock(String userKey, String sessionId) {
        return Optional.ofNullable(mapper.lockSession(userKey, sessionId));
    }
    /** 幂等撤销指定 Run 的写入许可，返回实际撤销数量。 */
    public int retireRun(String runId, String actor) {
        return mapper.retireRun(runId, actor);
    }
    /** 返回保留期限已结束的会话坐标。 */
    public List<AgentStateSession> expired(LocalDateTime before, int limit) {
        return mapper.expired(before, limit);
    }
    /** 删除过期登记，后续写入因未注册继续被拒绝。 */
    public void delete(String sessionId) {
        mapper.deleteSession(sessionId);
    }
}
