package com.manga.agent.collaboration;

import com.manga.agent.blueprint.AgentBlueprint;
import com.manga.agent.domain.CreativeErrorCode;
import com.manga.agent.domain.SystemActor;
import com.manga.agent.execution.ClaimedCreativeStep;
import com.manga.agent.harness.AgentRuntimeException;
import com.manga.entity.CreativeRunBudget;
import com.manga.entity.CreativeStep;
import com.manga.repository.CreativeGraphRepository;
import com.manga.repository.CreativeRunRepository;
import com.manga.repository.CreativeStepRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.Objects;

/** 在 Run 锁内冻结和消耗预算；重试和进程重启均不能重置已经预留的额度。 */
@Service
@RequiredArgsConstructor
public class CreativeRunBudgetService {
    private final CreativeGraphRepository graph;
    private final CreativeRunRepository runs;
    private final CreativeStepRepository steps;
    private final CollaborationProperties properties;

    /** 受理事务内创建预算；已有运行首次使用时只初始化一次。 */
    @Transactional
    public CreativeRunBudget initialize(String runId) {
        runs.lockByRunId(runId);
        CreativeRunBudget existing = graph.budget(runId);
        if (Objects.nonNull(existing)) return existing;
        CreativeRunBudget budget = new CreativeRunBudget();
        budget.setRunId(runId);
        budget.setMaxSteps(properties.maxSteps());
        budget.setMaxDepth(properties.maxDepth());
        budget.setMaxParallel(properties.maxParallel());
        budget.setMaxRepairRounds(properties.maxRepairRounds());
        budget.setMaxInvocations(properties.maxInvocations());
        budget.setMaxReservedTurns(properties.maxReservedTurns());
        budget.setUsedInvocations(0);
        budget.setReservedTurns(0);
        budget.setCreatedBy(SystemActor.CREATIVE_RUNTIME.code());
        budget.setUpdatedBy(SystemActor.CREATIVE_RUNTIME.code());
        graph.create(budget);
        return budget;
    }

    /** 受理事务内将平台预算收窄到根 Profile 的冻结上限，不影响已有运行。 */
    @Transactional
    public void constrain(String runId, AgentBlueprint blueprint) {
        if (Objects.isNull(blueprint.executionPolicy())) return;
        runs.lockByRunId(runId);
        var budget = initialize(runId);
        var limits = blueprint.executionPolicy().budget();
        budget.setMaxSteps(Math.min(budget.getMaxSteps(), limits.maxSteps()));
        budget.setMaxDepth(Math.min(budget.getMaxDepth(), limits.maxDepth()));
        budget.setMaxParallel(Math.min(budget.getMaxParallel(), limits.maxParallel()));
        budget.setMaxRepairRounds(Math.min(budget.getMaxRepairRounds(), limits.maxRepairRounds()));
        budget.setMaxInvocations(Math.min(budget.getMaxInvocations(), limits.maxInvocations()));
        budget.setMaxReservedTurns(Math.min(budget.getMaxReservedTurns(), limits.maxReservedTurns()));
        graph.update(budget);
    }

    /** 一个 owner epoch 只预留一次；额度按最大轮数保守计算，失败不退款。 */
    @Transactional
    public void reserve(ClaimedCreativeStep claim, AgentBlueprint blueprint) {
        runs.lockByRunId(claim.runId());
        CreativeStep step = steps.findByStepId(claim.stepId()).orElseThrow();
        CollaborationGuard.requireOwner(step, claim.ownerInstanceId(), claim.ownerEpoch(), claim.stateSessionId());
        if (Objects.equals(step.getReservedEpoch(), claim.ownerEpoch())) return;
        CreativeRunBudget budget = initialize(claim.runId());
        int turns = blueprint.maxTurns() + 1;
        if (Objects.nonNull(step.getParentStepId()) && Objects.nonNull(blueprint.executionPolicy())) {
            var limits = blueprint.executionPolicy().budget();
            if (step.getAttemptNo() > limits.maxInvocations() || (long) step.getAttemptNo() * turns > limits.maxReservedTurns()) {
                throw new AgentRuntimeException(CreativeErrorCode.BUDGET_EXCEEDED, CollaborationMessages.BUDGET_EXCEEDED);
            }
        }
        if (budget.getUsedInvocations() >= budget.getMaxInvocations()
                || (long) budget.getReservedTurns() + turns > budget.getMaxReservedTurns()) {
            throw new AgentRuntimeException(CreativeErrorCode.BUDGET_EXCEEDED, CollaborationMessages.BUDGET_EXCEEDED);
        }
        budget.setUsedInvocations(budget.getUsedInvocations() + 1);
        budget.setReservedTurns(budget.getReservedTurns() + turns);
        budget.setUpdatedBy(SystemActor.STEP_EXECUTION.code());
        graph.update(budget);
        step.setReservedEpoch(claim.ownerEpoch());
        graph.updateMetadata(step);
    }
}
