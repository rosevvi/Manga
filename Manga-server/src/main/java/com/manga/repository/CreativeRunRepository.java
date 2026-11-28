package com.manga.repository;

import java.time.LocalDateTime;

import com.manga.agent.domain.CreativeOperationType;
import com.manga.agent.domain.CreativeRunStatus;
import com.manga.entity.CreativeRun;
import com.manga.mapper.CreativeRunMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import com.manga.agent.api.CreativeConversationHistory;

/**
 * 封装 Creative Run 的持久化访问。
 */
@Repository
@RequiredArgsConstructor
public class CreativeRunRepository {

    private final CreativeRunMapper mapper;
    /** 暂停或恢复时只修改计时字段，避免旧快照覆盖事件序号。 */
    public void updateDeadline(String runId, LocalDateTime deadline, String actor) {
        mapper.updateDeadline(runId, deadline, actor);
    }
    /** 读取会话最近运行的恢复水位，历史接口不强制同步投影。 */
    public List<CreativeConversationHistory.ProjectionWatermark> projectionWatermarks(String conversationId, int limit) {
        return mapper.projectionWatermarks(conversationId, limit);
    }
    /** 按用户和项目读取手动任务历史。 */
    public List<CreativeRun> findManual(long userId, Long projectId, Long beforeId, int limit) {
        return mapper.findManual(userId, projectId, beforeId, limit);
    }
    /** 查询项目内最新活动的手动生成任务。 */
    public Optional<CreativeRun> findActiveManual(long userId, Long projectId) {
        return Optional.ofNullable(mapper.findActiveManual(userId, projectId));
    }

    /** 为悬浮入口查询跨项目运行状态，所有状态均限定在当前账号。 */
    public boolean hasRunningTask(long userId) { return mapper.hasRunningTask(userId); }

    /** 返回指定用户、项目和操作目标下最新的手动任务。 */
    public Optional<CreativeRun> findLatestManualForTarget(long userId, long projectId, long targetId,
            CreativeOperationType operationType, CreativeRunStatus status) {
        return Optional.ofNullable(mapper.findLatestManualForTarget(userId, projectId, targetId, operationType, status));
    }
    /** 查找需要推进消息投影的会话 Run。 */
    public List<String> findUnprojected(String conversationId, int limit) {
        return mapper.findUnprojected(conversationId, limit);
    }
    /** 查找会话当前活动运行。 */
    public Optional<CreativeRun> findActiveConversation(String conversationId) {
        return Optional.ofNullable(mapper.findActiveConversation(conversationId));
    }
    /** 按受理顺序读取会话运行标识。 */
    public List<String> findConversationRunIds(String conversationId) {
        return mapper.findConversationRunIds(conversationId);
    }
    /** 持有 Run 行锁时原子保存消息投影水位。 */
    public void updateProjectionCursor(String runId, long sequence, String actor) {
        mapper.updateProjectionCursor(runId, sequence, actor);
    }

    /** 新增 Creative Run 并回填数据库主键。 */
    public CreativeRun create(CreativeRun run) {
        mapper.insert(run);
        return run;
    }

    /** 按稳定 Run 标识查询运行快照。 */
    public Optional<CreativeRun> findByRunId(String runId) {
        return Optional.ofNullable(mapper.findByRunId(runId));
    }

    /** 查询用户拥有的指定 Run。 */
    public Optional<CreativeRun> findOwned(String runId, long userId) {
        return Optional.ofNullable(mapper.findOwned(runId, userId));
    }

    /** 按用户和幂等请求标识查询已存在的 Run。 */
    public Optional<CreativeRun> findByRequestId(long userId, String requestId) {
        return Optional.ofNullable(mapper.findByRequestId(userId, requestId));
    }

    /** 加行锁查询用户拥有的 Run。 */
    public Optional<CreativeRun> lockOwned(String runId, long userId) {
        return Optional.ofNullable(mapper.lockOwned(runId, userId));
    }

    /** 加行锁查询 Run，供事件序号分配使用。 */
    public CreativeRun lockByRunId(String runId) {
        return mapper.lockByRunId(runId);
    }

    /** 更新 Run 的状态、租约或事件序号。 */
    public void update(CreativeRun run) {
        mapper.updateById(run);
    }
}
