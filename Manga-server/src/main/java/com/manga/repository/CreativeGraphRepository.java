package com.manga.repository;

import com.manga.entity.CreativeDelegation;
import com.manga.entity.CreativeRunBudget;
import com.manga.entity.CreativeStep;
import com.manga.entity.CreativeStepDependency;
import com.manga.mapper.CreativeArtifactMapper;
import com.manga.mapper.CreativeDelegationMapper;
import com.manga.mapper.CreativeRunBudgetMapper;
import com.manga.mapper.CreativeStepDependencyMapper;
import com.manga.mapper.CreativeStepMapper;
import com.manga.mapper.CreativeGraphMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Set;
import java.util.Objects;

/** 封装协作图的简单 CRUD 和批量读取；领域判断留在应用服务。 */
@Repository
@RequiredArgsConstructor
public class CreativeGraphRepository {
    private final CreativeGraphMapper queries;
    private final CreativeDelegationMapper plans;
    private final CreativeStepDependencyMapper dependencies;
    private final CreativeRunBudgetMapper budgets;
    private final CreativeStepMapper steps;
    private final CreativeArtifactMapper artifacts;

    /** 一次读取 Run 的全部计划。 */
    public List<CreativeDelegation> plans(String runId) { return queries.plans(runId); }
    /** 一次读取 Run 的全部边。 */
    public List<CreativeStepDependency> dependencies(String runId) { return queries.dependencies(runId); }
    /** 读取受理时冻结的预算。 */
    public CreativeRunBudget budget(String runId) { return queries.budget(runId); }
    /** 扫描需要补偿的 Run 标识。 */
    public List<String> pendingRuns(int limit) { return queries.pendingRuns(limit); }
    /** 创建冻结计划检查点。 */
    public void create(CreativeDelegation plan) { plans.insert(plan); }
    /** 创建已经验证无环的边。 */
    public void create(CreativeStepDependency edge) { dependencies.insert(edge); }
    /** 创建受理预算。 */
    public void create(CreativeRunBudget budget) { budgets.insert(budget); }
    /** 更新已加锁计划。 */
    public void update(CreativeDelegation plan) { plans.updateById(plan); }
    /** 更新已加锁预算。 */
    public void update(CreativeRunBudget budget) { budgets.updateById(budget); }
    /** 更新持有 Run 锁且已验证 owner 的步骤元数据，不用于撤销执行租约。 */
    public void updateMetadata(CreativeStep step) { steps.updateById(step); }

    /** 单次批量校验产物归属，拒绝跨项目引用。 */
    public boolean artifactsBelongTo(Set<Long> ids, Long projectId, long userId) {
        if (ids.isEmpty()) return true;
        var found = artifacts.selectByIds(ids);
        return found.size() == ids.size() && found.stream().allMatch(artifact ->
                Objects.equals(artifact.getProjectId(), projectId) && Objects.equals(artifact.getUserId(), userId));
    }
}
