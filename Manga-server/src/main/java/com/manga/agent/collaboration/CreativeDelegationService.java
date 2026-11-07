package com.manga.agent.collaboration;

import com.manga.agent.blueprint.AgentBlueprintCodec;
import com.manga.agent.capability.AgentRuntimeContext;
import com.manga.agent.capability.CapabilityCommand;
import com.manga.agent.capability.CapabilityContractSupport;
import com.manga.agent.changeset.ChangeSetCodec;
import com.manga.agent.domain.CreativeActor;
import com.manga.agent.domain.CreativeErrorCode;
import com.manga.agent.harness.AgentRuntimeException;
import com.manga.agent.profile.AgentProfileKey;
import com.manga.entity.CreativeDelegation;
import com.manga.repository.CreativeChangeSetRepository;
import com.manga.repository.CreativeGraphRepository;
import com.manga.repository.CreativeRunRepository;
import com.manga.repository.CreativeStepRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import java.util.Objects;
import java.util.UUID;

/** 工具事务只提交冻结计划；不在模型回调栈内调度或等待其他 Agent。 */
@Service
@RequiredArgsConstructor
public class CreativeDelegationService {
    private final CreativeRunRepository runs;
    private final CreativeStepRepository steps;
    private final CreativeGraphRepository graph;
    private final CreativeChangeSetRepository proposals;
    private final ChangeSetCodec codec;
    private final AgentBlueprintCodec blueprints;
    private final WorkerBlueprintFactory workers;
    private final CreativeRunBudgetService budgets;
    private final CollaborationProperties properties;

    /** 按工具调用、执行代次和父步骤轮数去重，提交后由执行器关闭 Harness 并物化。 */
    @Transactional
    public Receipt delegate(CapabilityCommand command, AgentRuntimeContext context) {
        var run = runs.lockByRunId(context.runId());
        var parent = steps.findByStepId(context.stepId()).orElse(null);
        CollaborationGuard.requireRun(run, parent, context);
        var blueprint = blueprints.deserialize(parent.getBlueprintSnapshotJson());
        if (Objects.nonNull(parent.getParentStepId()) || blueprint.profileKey() != AgentProfileKey.MANGA_DIRECTOR
                || Objects.isNull(run.getProjectId()) || Objects.nonNull(parent.getApprovalResultJson())
                || proposals.findByStep(parent.getStepId()).isPresent()
                || !StringUtils.hasText(command.toolCallId()) || command.toolCallId().length() > 128) invalid();
        DelegatePlanRequest request = codec.validate(command.input(), DelegatePlanRequest.class);
        CreativePlanValidator.validate(request, properties.maxBatchSize());
        String fingerprint = CapabilityContractSupport.sha256(codec.write(request));
        var plans = graph.plans(run.getRunId());
        var existing = plans.stream().filter(plan -> plan.getParentStepId().equals(parent.getStepId()))
                .filter(plan -> plan.getTriggerToolCallId().equals(command.toolCallId())
                        || plan.getOwnerEpoch() == context.ownerEpoch() || plan.getStatus() == DelegationStatus.DRAFT)
                .findFirst().orElse(null);
        if (Objects.nonNull(existing)) {
            if (!existing.getRequestFingerprint().equals(fingerprint)) {
                throw new AgentRuntimeException(CreativeErrorCode.INVALID_REQUEST, CollaborationMessages.CHECKPOINT_CONFLICT);
            }
            return new Receipt(existing.getPlanId(), existing.getStatus(), existing.getRoundNo());
        }
        var budget = budgets.initialize(run.getRunId());
        int round = Objects.requireNonNullElse(parent.getDelegationRound(), 0) + 1;
        if (steps.findByRunId(run.getRunId()).size() + request.nodes().size() + 1 > budget.getMaxSteps()
                || Objects.requireNonNullElse(parent.getDelegationDepth(), 0) + 1 > budget.getMaxDepth()
                || round > budget.getMaxRepairRounds() + 1) {
            throw new AgentRuntimeException(CreativeErrorCode.BUDGET_EXCEEDED, CollaborationMessages.BUDGET_EXCEEDED);
        }
        FrozenDelegation frozen = new FrozenDelegation(request, request.nodes().stream()
                .map(node -> workers.freeze(node.profile(), blueprint)).toList());
        String actor = CreativeActor.user(context.userId());
        CreativeDelegation plan = new CreativeDelegation();
        plan.setPlanId(UUID.randomUUID().toString());
        plan.setRunId(run.getRunId());
        plan.setParentStepId(parent.getStepId());
        plan.setTriggerToolCallId(command.toolCallId());
        plan.setOwnerEpoch(context.ownerEpoch());
        plan.setRoundNo(round);
        plan.setTitle(request.title());
        plan.setStatus(DelegationStatus.DRAFT);
        plan.setFrozenJson(codec.write(frozen));
        plan.setRequestFingerprint(fingerprint);
        plan.setCreatedBy(actor);
        plan.setUpdatedBy(actor);
        graph.create(plan);
        return new Receipt(plan.getPlanId(), plan.getStatus(), round);
    }

    /** 是否已经提交本步骤的暂停检查点。 */
    @Transactional(readOnly = true)
    public boolean hasDraft(String runId, String stepId) {
        return graph.plans(runId).stream().anyMatch(plan -> plan.getParentStepId().equals(stepId)
                && plan.getStatus() == DelegationStatus.DRAFT);
    }

    /** 非 Director、已提交审批或缺失可信工具标识时拒绝委派。 */
    private void invalid() {
        throw new AgentRuntimeException(CreativeErrorCode.INVALID_REQUEST, CollaborationMessages.INVALID_PLAN);
    }

    /** 工具返回引用，不把完整 Worker 提示词或执行规格暴露给模型。 */
    public record Receipt(
            /** 持久化计划标识。 */ String planId,
            /** 尚未物化或已提交的状态。 */ DelegationStatus status,
            /** 当前计划轮数。 */ int round
    ) { }
}
