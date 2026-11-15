package com.manga.agent.collaboration;

import com.manga.entity.CreativeStep;
import java.util.stream.Collectors;

import com.manga.agent.api.RepairDecisionRequest;
import com.manga.agent.api.RepairDecisionView;
import com.manga.agent.api.CreativeRuntimeResponseCode;
import com.manga.agent.application.CancelCreativeRunCommand;
import com.manga.agent.application.CreativeEventService;
import com.manga.agent.application.CreativeRunAggregationService;
import com.manga.agent.application.CreativeRunCancellationService;
import com.manga.agent.changeset.ChangeSetCodec;
import com.manga.agent.changeset.CreativeRunStopped;
import com.manga.agent.domain.CreativeActor;
import com.manga.agent.domain.CreativeErrorCode;
import com.manga.agent.domain.CreativeEventType;
import com.manga.agent.domain.CreativeRunStatus;
import com.manga.agent.domain.CreativeStepStatus;
import com.manga.agent.domain.SystemActor;
import com.manga.agent.event.CreativeEventSources;
import com.manga.common.enums.CommonResponseCode;
import com.manga.common.exception.BusinessException;
import com.manga.entity.CreativeRepairDecision;
import com.manga.entity.CreativeRun;
import com.manga.mapper.CreativeGraphMapper;
import com.manga.repository.CreativeRepairDecisionRepository;
import com.manga.repository.CreativeRunRepository;
import com.manga.repository.CreativeStepRepository;
import com.manga.service.ProjectRevisionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** 用户修订命令与预算、版本、计时、图物化同事务提交；不发起模型调用。 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CreativeRepairDecisionService {
    private final CreativeRunRepository runs;
    private final CreativeStepRepository steps;
    private final CreativeRepairDecisionRepository decisions;
    private final CreativeGraphService graph;
    private final CreativeGraphMapper transitions;
    private final CreativeRunAggregationService aggregation;
    private final CreativeRunCancellationService cancellation;
    private final ProjectRevisionService revisions;
    private final CreativeEventService events;
    private final ChangeSetCodec codec;

    /** 一次校验所有权后读取该 Run 的确认历史与对应候选。 */
    @Transactional(readOnly = true)
    public List<RepairDecisionView> list(String runId, long userId) {
        runs.findOwned(runId, userId).orElseThrow(this::unavailable);
        var indexed = steps.findByRunId(runId).stream().collect(Collectors.toMap(
                step -> step.getStepId(), step -> step));
        return decisions.byRun(runId).stream().map(item -> view(item, indexed.get(item.getCandidateStepId()),
                indexed.get(item.getReviewStepId()))).toList();
    }

    /** Run 锁串行化决定；重复相同命令返回既有计划，不同决定拒绝覆盖。 */
    @Transactional
    public RepairDecisionView decide(String runId, String decisionId, long userId, RepairDecisionRequest request) {
        var run = runs.lockOwned(runId, userId).orElseThrow(this::unavailable);
        var decision = decisions.byRun(runId).stream().filter(value -> value.getDecisionId().equals(decisionId))
                .findFirst().orElseThrow(this::unavailable);
        if (decision.getStatus() != RepairDecisionStatus.PENDING) {
            if (decision.getAction() == request.action() && Objects.equals(decision.getComment(), request.comment())) {
                return view(decision);
            }
            throw new BusinessException(CreativeRuntimeResponseCode.IDEMPOTENCY_KEY_CONFLICT, RepairMessages.DECIDED, HttpStatus.CONFLICT);
        }
        LocalDateTime now = LocalDateTime.now();
        if (!decision.getExpiresAt().isAfter(now)) {
            expireLocked(run, decision, now);
            return view(decision);
        }
        var parent = steps.findByStepId(decision.getParentStepId()).orElseThrow(this::unavailable);
        if (run.getStatus().isTerminal() || parent.getStatus() != CreativeStepStatus.WAITING_REPAIR_CONFIRMATION) {
            throw new BusinessException(CreativeRuntimeResponseCode.ACTIVE_RUN_CONFLICT, RepairMessages.DECIDED, HttpStatus.CONFLICT);
        }
        String actor = CreativeActor.user(userId);
        decision.setAction(request.action()); decision.setRequestId(request.requestId());
        decision.setComment(request.comment()); decision.setDecidedAt(now); decision.setUpdatedBy(actor);
        if (request.action() == RepairAction.STOP) {
            decision.setStatus(RepairDecisionStatus.STOPPED); decisions.update(decision); decided(decision);
            cancellation.cancel(new CancelCreativeRunCommand(runId, userId));
        } else if (!revisions.matches(run.getProjectId(), userId, decision.getBaseProjectRevision(),
                decision.getChapterId(), decision.getBaseChapterRevision())) {
            decision.setStatus(RepairDecisionStatus.CONFLICTED); decisions.update(decision); decided(decision);
            transitions.transition(parent.getStepId(), parent.getStatus(), CreativeStepStatus.FAILED, null, null, null,
                    CreativeErrorCode.REPAIR_CONTEXT_CONFLICT.code(), RepairMessages.CONFLICT,
                    CollaborationToolContract.RETRY_ATTEMPTS, actor);
            aggregation.aggregate(runId);
        } else {
            run.setStatus(CreativeRunStatus.ACTIVE);
            run.setDeadlineAt(now.plusNanos(decision.getRemainingExecutionMillis() * 1_000_000L));
            run.setUpdatedBy(actor); runs.update(run);
            decision.setRepairPlanId(graph.resumeRepair(run, parent, decision, request.comment()));
            decision.setStatus(RepairDecisionStatus.CONFIRMED); decisions.update(decision); decided(decision);
        }
        log.info("Creative repair decided runId={} decisionId={} status={} userId={}", runId, decisionId, decision.getStatus(), userId);
        return view(decision);
    }

    /** 逐确认单事务收敛等待过期；补偿扫描重复执行不会重复取消。 */
    @Transactional
    public void expire(String runId, String decisionId) {
        var run = runs.lockByRunId(runId);
        if (Objects.isNull(run)) {
            return;
        }
        decisions.byRun(runId).stream().filter(item -> item.getDecisionId().equals(decisionId)
                && item.getStatus() == RepairDecisionStatus.PENDING && !item.getExpiresAt().isAfter(LocalDateTime.now()))
                .findFirst().ifPresent(item -> expireLocked(run, item, LocalDateTime.now()));
    }

    /** 手动取消也关闭确认单，终态后不允许晚到确认创建新计划。 */
    @EventListener
    @Transactional
    public void stopped(CreativeRunStopped signal) {
        runs.lockByRunId(signal.runId());
        for (var item : decisions.byRun(signal.runId())) {
            if (item.getStatus() == RepairDecisionStatus.PENDING) {
                item.setStatus(RepairDecisionStatus.STOPPED); item.setDecidedAt(LocalDateTime.now());
                item.setUpdatedBy(SystemActor.CREATIVE_RUNTIME.code()); decisions.update(item);
            }
        }
    }

    /** 到期与取消复用同一领域路径，不删除未批准候选。 */
    private void expireLocked(CreativeRun run, CreativeRepairDecision item, LocalDateTime now) {
        item.setStatus(RepairDecisionStatus.EXPIRED); item.setDecidedAt(now);
        item.setUpdatedBy(SystemActor.RECONCILIATION.code()); decisions.update(item); decided(item);
        cancellation.cancel(new CancelCreativeRunCommand(run.getRunId(), run.getUserId()));
    }

    /** 命令事实只包含标识、状态和轮次，不公开用户评论及模型提示词。 */
    private void decided(CreativeRepairDecision item) {
        events.append(item.getRunId(), item.getParentStepId(), CreativeEventType.REPAIR_CONFIRMATION_DECIDED,
                CreativeEventSources.RUNTIME, item.getDecisionId(), Map.of("decisionId", item.getDecisionId(),
                        "status", item.getStatus(), "round", item.getRoundNo()));
    }

    /** 从已验证结果读取用户可见候选，原始冻结执行规格不进入响应。 */
    private RepairDecisionView view(CreativeRepairDecision item) {
        var candidate = steps.findByStepId(item.getCandidateStepId()).orElseThrow();
        var review = steps.findByStepId(item.getReviewStepId()).orElseThrow();
        return view(item, candidate, review);
    }

    /** 列表批量装载结果，历史轮数增加也不产生 N+1 查询。 */
    private RepairDecisionView view(CreativeRepairDecision item, CreativeStep candidate, CreativeStep review) {
        return new RepairDecisionView(item.getDecisionId(), item.getRunId(), item.getSourcePlanId(), item.getRoundNo(),
                item.getCandidateStepId(), item.getReviewStepId(), item.getStatus(),
                codec.read(review.getResultJson(), CreativeStepResult.class).issues(),
                codec.read(candidate.getResultJson(), CreativeStepResult.class).candidate(), item.getComment(),
                item.getExpiresAt(), item.getDecidedAt(), item.getRepairPlanId());
    }

    /** 不区分不存在与其他用户的确认单。 */
    private BusinessException unavailable() {
        return new BusinessException(CommonResponseCode.FORBIDDEN, RepairMessages.UNAVAILABLE, HttpStatus.FORBIDDEN);
    }
}
