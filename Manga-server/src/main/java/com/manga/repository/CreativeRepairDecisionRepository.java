package com.manga.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.manga.agent.collaboration.RepairDecisionStatus;
import com.manga.entity.CreativeRepairDecision;
import com.manga.mapper.CreativeRepairDecisionMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import java.time.LocalDateTime;
import java.util.List;

/** 简单查询确认单，状态修改由持有 Run 锁的领域服务执行。 */
@Repository
@RequiredArgsConstructor
public class CreativeRepairDecisionRepository {
    private final CreativeRepairDecisionMapper mapper;
    /** 查询运行下的全部确认历史。 */
    public List<CreativeRepairDecision> byRun(String runId) {
        return mapper.selectList(new LambdaQueryWrapper<CreativeRepairDecision>()
                .eq(CreativeRepairDecision::getRunId, runId).orderByAsc(CreativeRepairDecision::getId));
    }
    /** 保存一次确认检查点。 */
    public void create(CreativeRepairDecision decision) { mapper.insert(decision); }
    /** 更新持有 Run 锁的确认单。 */
    public void update(CreativeRepairDecision decision) {
        decision.setUpdatedAt(LocalDateTime.now());
        mapper.updateById(decision);
    }
    /** 按独立索引有界扫描到期确认单。 */
    public List<CreativeRepairDecision> expired(LocalDateTime now, int limit) {
        return mapper.expired(now, limit);
    }
}
