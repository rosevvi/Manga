package com.manga.agent.collaboration;

import java.util.Map;

import com.manga.agent.application.CreativeEventService;
import com.manga.agent.changeset.ChangeSetCodec;
import com.manga.agent.domain.CreativeErrorCode;
import com.manga.agent.domain.CreativeEventType;
import com.manga.agent.domain.CreativeResourceType;
import com.manga.agent.domain.CreativeStepStatus;
import com.manga.agent.domain.SystemActor;
import com.manga.agent.event.CreativeEventSources;
import com.manga.agent.state.AgentStateSessionService;
import com.manga.entity.CreativeDelegation;
import com.manga.entity.CreativeRepairDecision;
import com.manga.entity.CreativeRun;
import com.manga.entity.CreativeStep;
import com.manga.mapper.CreativeGraphMapper;
import com.manga.repository.CreativeGraphRepository;
import com.manga.repository.CreativeRepairDecisionRepository;
import com.manga.repository.CreativeRunRepository;
import com.manga.service.ProjectRevisionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** 汇合事务内暂停未通过评审的父节点，不恢复模型，也不持有执行资源。 */
@Service
@RequiredArgsConstructor
public class CreativeRepairCheckpointService {
    private final CreativeRepairDecisionRepository decisions;
    private final CreativeGraphRepository graph;
    private final CreativeGraphMapper transitions;
    private final CreativeRunRepository runs;
    private final CreativeEventService events;
    private final AgentStateSessionService sessions;
    private final ProjectRevisionService revisions;
    private final ChangeSetCodec codec;
    private final CreativeRepairProperties properties;

    /** 调用者已持有 Run 锁；返回 true 表示已暂停或因修订预算耗尽结束父节点。 */
    public boolean pause(CreativeRun run, CreativeStep parent, CreativeDelegation plan, List<CreativeStep> current) {
        var children = current.stream().filter(step -> plan.getPlanId().equals(step.getDelegationPlanId())
                && Objects.nonNull(step.getResultJson())).toList();
        var review = children.stream().filter(step -> result(step).kind() == StepResultKind.REVIEW).findFirst().orElse(null);
        if (Objects.isNull(review) || result(review).verdict() != ReviewVerdict.NEEDS_REPAIR) {
            return false;
        }
        String actor = SystemActor.CREATIVE_RUNTIME.code();
        if (plan.getRoundNo() > graph.budget(run.getRunId()).getMaxRepairRounds()) {
            transitions.transition(parent.getStepId(), CreativeStepStatus.WAITING_CHILDREN, CreativeStepStatus.FAILED,
                    null, null, null, CreativeErrorCode.REPAIR_BUDGET_EXHAUSTED.code(), RepairMessages.EXHAUSTED,
                    CollaborationToolContract.RETRY_ATTEMPTS, actor);
            events.append(run.getRunId(), parent.getStepId(), CreativeEventType.STEP_FAILED,
                    CreativeEventSources.RUNTIME, plan.getPlanId(), Map.of("errorMessage", RepairMessages.EXHAUSTED));
            return true;
        }
        var candidate = children.stream().filter(step -> result(step).kind() == StepResultKind.SCRIPT_DRAFT
                || result(step).kind() == StepResultKind.STORYBOARD_DRAFT).max(Comparator.comparing(CreativeStep::getId))
                .orElseThrow(() -> new IllegalStateException(RepairMessages.INVALID_SOURCE));
        if (decisions.byRun(run.getRunId()).stream().anyMatch(item -> item.getReviewStepId().equals(review.getStepId()))) {
            return true;
        }
        LocalDateTime now = LocalDateTime.now();
        CreativeRepairDecision decision = new CreativeRepairDecision();
        decision.setDecisionId(UUID.randomUUID().toString());
        decision.setRunId(run.getRunId()); decision.setParentStepId(parent.getStepId());
        decision.setSourcePlanId(plan.getPlanId()); decision.setRoundNo(plan.getRoundNo());
        decision.setCandidateStepId(candidate.getStepId()); decision.setReviewStepId(review.getStepId());
        decision.setIssuesJson(codec.write(result(review).issues()));
        decision.setBaseProjectRevision(candidate.getBaseProjectRevision());
        if (run.getTargetType() == CreativeResourceType.CHAPTER) {
            decision.setChapterId(run.getTargetId());
            decision.setBaseChapterRevision(revisions.chapter(run.getProjectId(), run.getTargetId()).getRevision());
        }
        decision.setRemainingExecutionMillis(Math.max(1, Duration.between(now, run.getDeadlineAt()).toMillis()));
        decision.setPausedAt(now); decision.setExpiresAt(now.plus(properties.confirmationTtl()));
        decision.setStatus(RepairDecisionStatus.PENDING); decision.setCreatedBy(actor); decision.setUpdatedBy(actor);
        decisions.create(decision);
        if (transitions.transition(parent.getStepId(), CreativeStepStatus.WAITING_CHILDREN,
                CreativeStepStatus.WAITING_REPAIR_CONFIRMATION, null, null, null, null, null,
                CollaborationToolContract.RETRY_ATTEMPTS, actor) != 1) {
            throw new IllegalStateException(CollaborationMessages.CHECKPOINT_CONFLICT);
        }
        sessions.retireSession(parent.getStateSessionId());
        run.setDeadlineAt(decision.getExpiresAt());
        runs.updateDeadline(run.getRunId(), decision.getExpiresAt(), actor);
        events.append(run.getRunId(), parent.getStepId(), CreativeEventType.REPAIR_CONFIRMATION_REQUIRED,
                CreativeEventSources.RUNTIME, decision.getDecisionId(), Map.of(
                        "decisionId", decision.getDecisionId(), "round", plan.getRoundNo(),
                        "candidateStepId", candidate.getStepId(), "reviewStepId", review.getStepId(),
                        "issues", result(review).issues(), "expiresAt", decision.getExpiresAt()));
        return true;
    }

    /** 已通过 schema 校验的 Worker 结果仅在本次 Run 内恢复。 */
    private CreativeStepResult result(CreativeStep step) {
        return codec.read(step.getResultJson(), CreativeStepResult.class);
    }
}
