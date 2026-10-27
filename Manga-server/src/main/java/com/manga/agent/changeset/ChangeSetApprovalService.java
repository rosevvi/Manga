package com.manga.agent.changeset;

import com.manga.agent.application.CreativeEventService;
import com.manga.agent.application.CreativeIdentifierGenerator;
import com.manga.agent.config.CreativeRuntimeProperties;
import com.manga.agent.domain.CreativeActor;
import com.manga.agent.domain.CreativeDispatchOutboxStatus;
import com.manga.agent.domain.CreativeEventType;
import com.manga.agent.domain.CreativeRunStatus;
import com.manga.agent.domain.CreativeStepStateMachine;
import com.manga.agent.domain.CreativeStepStatus;
import com.manga.agent.domain.SystemActor;
import com.manga.agent.event.CreativeEventSources;
import com.manga.agent.execution.ClaimedCreativeStep;
import com.manga.agent.execution.CreativeDispatchSignal;
import com.manga.agent.state.AgentStateSessionService;
import com.manga.common.exception.BusinessException;
import com.manga.entity.CreativeApproval;
import com.manga.entity.CreativeChangeSet;
import com.manga.entity.CreativeDispatchOutbox;
import com.manga.entity.CreativeRun;
import com.manga.entity.CreativeStep;
import com.manga.repository.CreativeApprovalRepository;
import com.manga.repository.CreativeChangeSetRepository;
import com.manga.repository.CreativeDispatchOutboxRepository;
import com.manga.repository.CreativeRunRepository;
import com.manga.repository.CreativeStepRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

/** 用 Run 行锁串行化开放审批、批准、拒绝、过期和取消竞争。 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ChangeSetApprovalService {
    private final CreativeChangeSetRepository proposals;
    private final CreativeApprovalRepository approvals;
    private final CreativeRunRepository runs;
    private final CreativeStepRepository steps;
    private final CreativeDispatchOutboxRepository outbox;
    private final AgentStateSessionService sessions;
    private final CreativeEventService events;
    private final ChangeSetApplicationService application;
    private final ChangeSetCodec codec;
    private final ApprovalProperties properties;
    private final CreativeRuntimeProperties runtimeProperties;
    private final CreativeIdentifierGenerator identifiers;
    private final ApplicationEventPublisher publisher;

    /** 工具结果后停止流，也用于进程重启后发现已落库草稿的步骤。 */
    @Transactional(readOnly = true)
    public boolean hasDraft(String stepId) {
        return proposals.findByStep(stepId).filter(value -> value.getStatus() == ChangeSetStatus.DRAFT).isPresent();
    }

    /** 执行资源关闭后调用；原子释放 owner、撤销旧状态许可并开放审批，不保留 Reactor 调用栈。 */
    @Transactional
    public boolean suspendIfProposed(ClaimedCreativeStep claim) {
        CreativeRun run = runs.lockByRunId(claim.runId());
        CreativeChangeSet proposal = proposals.findByStep(claim.stepId()).orElse(null);
        if (Objects.isNull(proposal) || proposal.getStatus() != ChangeSetStatus.DRAFT) return false;
        if (run.getStatus() != CreativeRunStatus.ACTIVE) return false;
        LocalDateTime now = LocalDateTime.now();
        CreativeStepStateMachine.requireTransition(CreativeStepStatus.RUNNING, CreativeStepStatus.WAITING_APPROVAL);
        if (!steps.pauseOwned(claim.stepId(), claim.ownerInstanceId(), claim.ownerEpoch(), now, SystemActor.CREATIVE_RUNTIME.code())) {
            throw new BusinessException(ChangeSetResponseCode.OWNER_LOST, HttpStatus.CONFLICT);
        }
        ChangeSetStateMachine.requireTransition(proposal.getStatus(), ChangeSetStatus.PENDING);
        proposal.setStatus(ChangeSetStatus.PENDING);
        proposal.setUpdatedBy(SystemActor.CREATIVE_RUNTIME.code());
        proposals.update(proposal);
        approvals.create(CreativeApproval.builder().changeSetId(proposal.getChangeSetId()).status(ApprovalStatus.PENDING)
                .expiresAt(now.plus(properties.ttl())).createdBy(proposal.getCreatedBy()).updatedBy(proposal.getUpdatedBy()).build());
        run.setStatus(CreativeRunStatus.WAITING);
        run.setLeaseUntil(null);
        runs.update(run);
        sessions.retire(run, false);
        events.append(run.getRunId(), claim.stepId(), CreativeEventType.CHANGESET_PROPOSED,
                CreativeEventSources.RUNTIME, proposal.getChangeSetId(), new ProposalEvent(proposal.getChangeSetId(), proposal.getKind()));
        events.append(run.getRunId(), claim.stepId(), CreativeEventType.APPROVAL_REQUIRED,
                CreativeEventSources.RUNTIME, proposal.getChangeSetId(), new ProposalEvent(proposal.getChangeSetId(), proposal.getKind()));
        log.info("Creative Step awaiting approval runId={} stepId={} changeSetId={}", run.getRunId(), claim.stepId(), proposal.getChangeSetId());
        return true;
    }

    /** 同一决定可幂等重放；批准、领域写入、审批人、结果和恢复 Outbox 在同一事务提交。 */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public ApprovalResult decide(String id, long userId, ApprovalDecision decision, String comment) {
        if (Objects.isNull(decision)) throw new BusinessException(ChangeSetResponseCode.INVALID);
        CreativeChangeSet candidate = requireProposal(id, userId);
        CreativeRun run = runs.lockOwned(candidate.getRunId(), userId)
                .orElseThrow(() -> new BusinessException(ChangeSetResponseCode.NOT_FOUND, HttpStatus.NOT_FOUND));
        CreativeChangeSet proposal = requireProposal(id, userId);
        CreativeApproval approval = approvals.find(id)
                .orElseThrow(() -> new BusinessException(ChangeSetResponseCode.STATE_CONFLICT, HttpStatus.CONFLICT));
        if (approval.getStatus() != ApprovalStatus.PENDING) {
            boolean sameDecision = decision == ApprovalDecision.APPROVE
                    ? approval.getStatus() == ApprovalStatus.APPROVED || approval.getStatus() == ApprovalStatus.CONFLICTED
                    : approval.getStatus() == ApprovalStatus.REJECTED;
            if (!sameDecision && approval.getStatus() != ApprovalStatus.EXPIRED && approval.getStatus() != ApprovalStatus.CANCELLED) {
                throw new BusinessException(ChangeSetResponseCode.STATE_CONFLICT, HttpStatus.CONFLICT);
            }
            return codec.read(proposal.getResultJson(), ApprovalResult.class);
        }
        if (run.getStatus().isTerminal() || run.getStatus() == CreativeRunStatus.CANCEL_REQUESTED) {
            return finish(proposal, approval, run, ApprovalStatus.CANCELLED, ChangeSetConstants.CANCELLED_COMMENT, null, List.of(), false);
        }
        requireWaiting(proposal);
        if (!approval.getExpiresAt().isAfter(LocalDateTime.now())) {
            return finish(proposal, approval, run, ApprovalStatus.EXPIRED, ChangeSetConstants.EXPIRED_COMMENT, null, List.of(), true);
        }
        if (decision == ApprovalDecision.REJECT) {
            return finish(proposal, approval, run, ApprovalStatus.REJECTED, comment, userId, List.of(), true);
        }
        ApprovalResult applied = application.apply(proposal, userId, CreativeActor.user(userId), comment);
        return finish(proposal, approval, run, applied.status(), comment, userId, applied.resourceIds(), true);
    }

    /** 有界扫描的单项补偿；和用户决定共用 Run 锁，不会重复恢复或应用。 */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void expire(String id) {
        CreativeChangeSet candidate = proposals.find(id).orElse(null);
        if (Objects.isNull(candidate)) return;
        CreativeRun run = runs.lockByRunId(candidate.getRunId());
        CreativeChangeSet proposal = proposals.find(id).orElseThrow();
        CreativeApproval approval = approvals.find(id).orElse(null);
        if (Objects.isNull(approval) || approval.getStatus() != ApprovalStatus.PENDING
                || approval.getExpiresAt().isAfter(LocalDateTime.now())) return;
        boolean resume = run.getStatus() == CreativeRunStatus.WAITING;
        finish(proposal, approval, run, resume ? ApprovalStatus.EXPIRED : ApprovalStatus.CANCELLED,
                resume ? ChangeSetConstants.EXPIRED_COMMENT : ChangeSetConstants.CANCELLED_COMMENT, null, List.of(), resume);
    }

    /** 将审批结果注入原始输入；恢复不读取当前 Profile 或重新编译 Blueprint。 */
    @Transactional(readOnly = true)
    public String instruction(ClaimedCreativeStep claim, String original) {
        CreativeStep step = steps.findByStepId(claim.stepId()).orElseThrow();
        if (Objects.isNull(step.getApprovalResultJson())) return original;
        CreativeChangeSet proposal = proposals.findByStep(claim.stepId()).orElseThrow();
        String title = null;
        int resourceCount;
        if (proposal.getKind() == ChangeSetKind.SCRIPT) {
            title = codec.read(proposal.getPayloadJson(), ScriptChangeSet.class).title();
            resourceCount = 1;
        } else {
            resourceCount = codec.read(proposal.getPayloadJson(), StoryboardChangeSet.class).shots().size();
        }
        ProposalResumeSummary summary = new ProposalResumeSummary(proposal.getChangeSetId(), proposal.getKind(),
                proposal.getChapterId(), title, resourceCount);
        // 正文留在不可变提案中；恢复只解释审批结果，避免把完整长章节再次灌入模型上下文。
        return ChangeSetConstants.RESUME_TEMPLATE.formatted(original, codec.write(summary), step.getApprovalResultJson());
    }

    /** 记录终态并创建逻辑恢复命令；在此之前发生的任何异常都会回滚业务写入。 */
    private ApprovalResult finish(CreativeChangeSet proposal, CreativeApproval approval, CreativeRun run,
            ApprovalStatus status, String comment, Long actorId, List<Long> ids, boolean resume) {
        ChangeSetStatus target = switch (status) {
            case APPROVED -> ChangeSetStatus.APPLIED;
            case REJECTED -> ChangeSetStatus.REJECTED;
            case CONFLICTED -> ChangeSetStatus.CONFLICTED;
            case EXPIRED -> ChangeSetStatus.EXPIRED;
            case CANCELLED -> ChangeSetStatus.CANCELLED;
            default -> throw new BusinessException(ChangeSetResponseCode.STATE_CONFLICT, HttpStatus.CONFLICT);
        };
        ChangeSetStateMachine.requireTransition(proposal.getStatus(), target);
        ApprovalStateMachine.requireDecision(approval.getStatus(), status);
        String actor = Objects.isNull(actorId) ? SystemActor.CREATIVE_RUNTIME.code() : CreativeActor.user(actorId);
        ApprovalResult result = new ApprovalResult(proposal.getChangeSetId(), status, comment, ids);
        proposal.setStatus(target);
        proposal.setResultJson(codec.write(result));
        proposal.setUpdatedBy(actor);
        proposals.update(proposal);
        approval.setStatus(status);
        approval.setComment(comment);
        approval.setDecidedAt(LocalDateTime.now());
        approval.setDecidedBy(actorId);
        approval.setUpdatedBy(actor);
        approvals.update(approval);
        events.append(run.getRunId(), proposal.getStepId(), CreativeEventType.APPROVAL_DECIDED,
                CreativeEventSources.RUNTIME, proposal.getChangeSetId(), result);
        if (status == ApprovalStatus.APPROVED || status == ApprovalStatus.CONFLICTED) {
            events.append(run.getRunId(), proposal.getStepId(), status == ApprovalStatus.APPROVED
                            ? CreativeEventType.CHANGESET_APPLIED : CreativeEventType.CHANGESET_CONFLICTED,
                    CreativeEventSources.RUNTIME, proposal.getChangeSetId(), result);
        }
        if (resume) resume(run, proposal, actor);
        log.info("Creative approval decided runId={} stepId={} changeSetId={} status={} actorId={}",
                run.getRunId(), proposal.getStepId(), proposal.getChangeSetId(), status, actorId);
        return result;
    }

    /** 新 StateSession 隔离旧 Harness 晚到写入，调度重试不重复执行业务应用。 */
    private void resume(CreativeRun run, CreativeChangeSet proposal, String actor) {
        // 前面的审批事件已推进 sequence/cursor，更新 deadline 前必须重新读取，不能覆盖事件水位。
        run = runs.lockByRunId(run.getRunId());
        String sessionId = identifiers.nextStateSessionId();
        CreativeStepStateMachine.requireTransition(CreativeStepStatus.WAITING_APPROVAL, CreativeStepStatus.READY);
        if (!steps.resumeApproval(proposal.getStepId(), sessionId, proposal.getResultJson(), ChangeSetConstants.RESUME_MAX_ATTEMPTS, actor)) {
            throw new BusinessException(ChangeSetResponseCode.STATE_CONFLICT, HttpStatus.CONFLICT);
        }
        run.setDeadlineAt(LocalDateTime.now().plus(runtimeProperties.runTimeout()));
        run.setUpdatedBy(actor);
        runs.update(run);
        sessions.register(run, sessionId);
        CreativeStep step = steps.findByStepId(proposal.getStepId()).orElseThrow();
        outbox.create(CreativeDispatchOutbox.builder().stepId(step.getStepId()).scheduleVersion(step.getScheduleVersion())
                .status(CreativeDispatchOutboxStatus.PENDING).attempts(0).createdBy(actor).updatedBy(actor).build());
        publisher.publishEvent(new CreativeDispatchSignal(step.getStepId(), step.getScheduleVersion()));
    }

    /** 校验提案归属，不通过客户端参数选择审批人。 */
    private CreativeChangeSet requireProposal(String id, long userId) {
        return proposals.find(id).filter(proposal -> proposal.getUserId() == userId)
                .orElseThrow(() -> new BusinessException(ChangeSetResponseCode.NOT_FOUND, HttpStatus.NOT_FOUND));
    }

    /** 仅持久化等待中的 Step 才能批准，不允许抢在资源释放前处理草稿。 */
    private void requireWaiting(CreativeChangeSet proposal) {
        if (proposal.getStatus() != ChangeSetStatus.PENDING || steps.findByStepId(proposal.getStepId())
                .filter(step -> step.getStatus() == CreativeStepStatus.WAITING_APPROVAL).isEmpty()) {
            throw new BusinessException(ChangeSetResponseCode.STATE_CONFLICT, HttpStatus.CONFLICT);
        }
    }

    /** SSE 仅传递提案引用，完整差异通过有权限校验的接口读取。 */
    public record ProposalEvent(
            /** 提案稳定标识。 */ String changeSetId,
            /** 提案业务类型。 */ ChangeSetKind kind
    ) { }

    /** 恢复阶段仅需引用和数量，完整差异仍由提案查询接口提供。 */
    private record ProposalResumeSummary(
            /** 不可变提案标识。 */ String changeSetId,
            /** 提案业务类型。 */ ChangeSetKind kind,
            /** 可选目标章节。 */ Long chapterId,
            /** 可选章节标题。 */ String title,
            /** 计划生成或修改的业务记录数量。 */ int resourceCount
    ) { }
}
