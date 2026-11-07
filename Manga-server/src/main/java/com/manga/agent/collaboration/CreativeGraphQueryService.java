package com.manga.agent.collaboration;

import com.manga.agent.application.CancelCreativeRunCommand;
import com.manga.agent.application.CreativeRunCancellationService;
import com.manga.agent.blueprint.AgentBlueprintCodec;
import com.manga.agent.changeset.ChangeSetCodec;
import com.manga.agent.domain.CreativeStepType;
import com.manga.common.enums.CommonResponseCode;
import com.manga.common.exception.BusinessException;
import com.manga.repository.CreativeRunRepository;
import com.manga.repository.CreativeStepRepository;
import com.manga.repository.CreativeGraphRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.Objects;

/** 对当前用户提供一致的持久化协作图快照及按失败策略停止步骤的入口。 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CreativeGraphQueryService {
    private final CreativeRunRepository runs;
    private final CreativeStepRepository steps;
    private final CreativeGraphRepository graph;
    private final ChangeSetCodec codec;
    private final AgentBlueprintCodec blueprints;
    private final CreativeRunCancellationService cancellation;

    /** 同一数据库快照内读取图，固定次数批量查询，不随节点数产生 N+1。 */
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public CreativeGraphView find(String runId, long userId) {
        var run = runs.findOwned(runId, userId).orElseThrow(() -> forbidden());
        var edges = graph.dependencies(runId);
        var nodes = steps.findByRunId(runId).stream().map(step -> {
            var blueprint = Objects.isNull(step.getBlueprintSnapshotJson()) ? null : blueprints.deserialize(step.getBlueprintSnapshotJson());
            var result = Objects.isNull(step.getResultJson()) ? null : codec.read(step.getResultJson(), CreativeStepResult.class);
            String waiting = switch (step.getStatus()) {
                case PENDING -> "等待前置步骤成功";
                case READY -> "等待执行容量";
                case WAITING_CHILDREN -> "等待协作步骤汇合";
                case WAITING_APPROVAL -> "等待用户审批";
                case WAITING_JOB -> "等待图片或视频作业";
                case WAITING_RETRY -> "等待重试";
                case CANCEL_REQUESTED -> "正在停止并释放执行资源";
                default -> null;
            };
            return new CreativeGraphView.Node(step.getStepId(), step.getParentStepId(), step.getDelegationPlanId(),
                    step.getStepType() == CreativeStepType.JOIN ? CollaborationToolContract.JOIN_NODE_KEY : step.getNodeKey(),
                    Objects.isNull(blueprint) ? null : blueprint.profileKey(), Objects.isNull(blueprint) ? null : blueprint.profileVersion(),
                    Objects.requireNonNullElse(step.getDelegationDepth(), 0), step.getStatus(), waiting,
                    edges.stream().filter(edge -> edge.getDownstreamStepId().equals(step.getStepId())).map(edge -> edge.getUpstreamStepId()).toList(),
                    step.getOwnerEpoch(), step.getAttemptNo(), Objects.isNull(result) ? null : result.content(),
                    Objects.isNull(result) ? null : result.verdict(), Objects.isNull(result) ? List.of() : result.issues(),
                    Objects.isNull(result) ? List.of() : result.artifactIds(), step.getErrorCode(), step.getErrorMessage(),
                    Objects.isNull(blueprint) ? null : blueprint.profileId(),
                    Objects.isNull(blueprint) || Objects.isNull(blueprint.executionPolicy()) ? null : blueprint.executionPolicy().displayName(),
                    Objects.nonNull(blueprint) && Objects.nonNull(blueprint.executionPolicy()) && blueprint.executionPolicy().sandbox());
        }).toList();
        var plans = graph.plans(runId).stream().map(plan -> new CreativeGraphView.Plan(plan.getPlanId(), plan.getParentStepId(),
                plan.getTitle(), plan.getRoundNo(), plan.getStatus(), JoinPolicy.ALL_SUCCESS, StepFailurePolicy.FAIL_RUN)).toList();
        var budget = graph.budget(runId);
        var view = Objects.isNull(budget) ? null : new CreativeGraphView.Budget(budget.getMaxSteps(), budget.getMaxDepth(),
                budget.getMaxParallel(), budget.getMaxRepairRounds(), budget.getMaxInvocations(), budget.getMaxReservedTurns(),
                budget.getUsedInvocations(), budget.getReservedTurns());
        return new CreativeGraphView(runId, run.getStatus(), run.getNextSequence() - 1, plans, nodes, view);
    }

    /** 首版 ALL_SUCCESS/FAIL_RUN 策略下停止单步会取消整个 Run，保留已完成结果和审计链。 */
    @Transactional
    public void cancelStep(String runId, String stepId, long userId) {
        runs.lockOwned(runId, userId).orElseThrow(() -> forbidden());
        var step = steps.findByStepId(stepId).filter(value -> value.getRunId().equals(runId)).orElseThrow(() -> forbidden());
        if (step.getStatus().isTerminal()) return;
        log.info("Creative Step stop requested runId={} stepId={} userId={} policy={}", runId, stepId, userId, StepFailurePolicy.FAIL_RUN);
        cancellation.cancel(new CancelCreativeRunCommand(runId, userId));
    }

    /** 对不存在和无权访问的运行使用相同响应，避免泄露标识是否有效。 */
    private BusinessException forbidden() { return new BusinessException(CommonResponseCode.FORBIDDEN, HttpStatus.FORBIDDEN); }
}
