package com.manga.repository;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.manga.entity.CreativeChangeSet;
import com.manga.mapper.CreativeChangeSetMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
/** 隔离提案聚合的持久化访问。 */
@Repository
@RequiredArgsConstructor
public class CreativeChangeSetRepository {
    private final CreativeChangeSetMapper mapper;
    /** 查询稳定提案标识。 */
    public Optional<CreativeChangeSet> find(String id) {
        return Optional.ofNullable(mapper.selectOne(new LambdaQueryWrapper<CreativeChangeSet>()
                .eq(CreativeChangeSet::getChangeSetId, id)));
    }
    /** 查询步骤的幂等提案。 */
    public Optional<CreativeChangeSet> findByStep(String stepId) { return Optional.ofNullable(mapper.findByStep(stepId)); }
    /** 批量查询运行提案。 */
    public List<CreativeChangeSet> findByRun(String runId) { return mapper.findByRun(runId); }
    /** 分页读取公开提案及历史审计。 */
    public List<CreativeChangeSet> findVisible(long userId, String runId, String conversationId, Long beforeId, int limit) {
        return mapper.findVisible(userId, runId, conversationId, beforeId, limit);
    }
    /** 首次保存不可变提案。 */
    public void create(CreativeChangeSet value) { mapper.insert(value); }
    /** 在持有 Run 锁时推进聚合状态。 */
    public void update(CreativeChangeSet value) { mapper.updateState(value); }
}
