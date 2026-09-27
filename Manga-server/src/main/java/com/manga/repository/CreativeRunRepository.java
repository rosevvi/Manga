package com.manga.repository;

import com.manga.entity.CreativeRun;
import com.manga.mapper.CreativeRunMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * 封装 Creative Run 的持久化访问。
 */
@Repository
@RequiredArgsConstructor
public class CreativeRunRepository {

    private final CreativeRunMapper mapper;

    /** 新增 Creative Run 并回填数据库主键。 */
    public CreativeRun create(CreativeRun run) {
        mapper.insert(run);
        return run;
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
