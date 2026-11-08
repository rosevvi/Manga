package com.manga.agent.collaboration;

import com.manga.agent.application.CreativeEventService;
import com.manga.agent.application.CreativeIdentifierGenerator;
import com.manga.agent.application.CreativeRunAggregationService;
import com.manga.agent.blueprint.AgentBlueprintCodec;
import com.manga.agent.blueprint.CapabilityManifest;
import com.manga.agent.changeset.ChangeSetCodec;
import com.manga.agent.changeset.CreativeRunStopped;
import com.manga.agent.domain.CreativeDispatchOutboxStatus;
import com.manga.agent.domain.CreativeErrorCode;
import com.manga.agent.domain.CreativeEventType;
import com.manga.agent.domain.CreativeRunStatus;
import com.manga.agent.domain.CreativeStepInput;
import com.manga.agent.domain.CreativeStepStatus;
import com.manga.agent.domain.CreativeStepType;
import com.manga.agent.domain.SystemActor;
import com.manga.agent.event.CreativeEventSources;
import com.manga.agent.event.StepCompletedEventPayload;
import com.manga.agent.event.StepCreatedEventPayload;
import com.manga.agent.execution.ClaimedCreativeStep;
import com.manga.agent.execution.CreativeDispatchSignal;
import com.manga.agent.harness.AgentRuntimeException;
import com.manga.agent.state.AgentStateSessionService;
import com.manga.entity.CreativeDelegation;
import com.manga.entity.CreativeDispatchOutbox;
import com.manga.entity.CreativeRun;
import com.manga.entity.CreativeStep;
import com.manga.entity.CreativeStepDependency;
import com.manga.mapper.CreativeGraphMapper;
import com.manga.repository.CreativeDispatchOutboxRepository;
import com.manga.repository.CreativeGraphRepository;
import com.manga.repository.CreativeRunRepository;
import com.manga.repository.CreativeStepRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Run 锁内物化子图、推进 ALL_SUCCESS 汇合及传播失败；不获取任何模型执行资源。 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CreativeGraphService {
    private final CreativeRunRepository runs;
    private final CreativeStepRepository steps;
    private final CreativeGraphRepository graph;
    private final CreativeGraphMapper transitions;
    private final CreativeDispatchOutboxRepository outbox;
    private final CreativeIdentifierGenerator identifiers;
    private final AgentBlueprintCodec blueprints;
    private final ChangeSetCodec codec;
    private final AgentStateSessionService sessions;
    private final CreativeEventService events;
    private final CreativeRunAggregationService aggregation;
    private final ApplicationEventPublisher publisher;

    /** 必须在模型流、HarnessLease 和 KernelHandle 已关闭后调用；草稿重放只物化一次。 */
    @Transactional
    public boolean suspendIfDelegated(ClaimedCreativeStep claim) {
        CreativeRun run = runs.lockByRunId(claim.runId());
        var plan = graph.plans(claim.runId()).stream().filter(value -> value.getParentStepId().equals(claim.stepId())
                && value.getStatus() == DelegationStatus.DRAFT).findFirst().orElse(null);
        if (Objects.isNull(plan) || run.getStatus() != CreativeRunStatus.ACTIVE) return false;
        CreativeStep parent = steps.findByStepId(claim.stepId()).orElseThrow();
        CollaborationGuard.requireOwner(parent, claim.ownerInstanceId(), claim.ownerEpoch(), claim.stateSessionId());
        FrozenDelegation frozen = codec.read(plan.getFrozenJson(), FrozenDelegation.class);
        CreativeStepInput input = codec.read(parent.getInputJson(), CreativeStepInput.class);
        Map<String, CreativeStep> nodes = new HashMap<>();
        for (int index = 0; index < frozen.request().nodes().size(); index++) {
            var node = frozen.request().nodes().get(index);
            var blueprint = frozen.blueprints().get(index);
            blueprints.verifyFingerprint(blueprint);
            CreativeStep child = newStep(parent, plan, node.key(), CreativeStepType.AGENT_INVOCATION);
            child.setInputJson(codec.write(new CreativeStepInput(node.instruction(), input.operationType(), input.target())));
            child.setBlueprintSnapshotJson(blueprints.serialize(blueprint));
            child.setBlueprintFingerprint(blueprint.fingerprint());
            child.setStateSessionId(identifiers.nextStateSessionId());
            steps.create(child);
            nodes.put(node.key(), child);
            created(child);
            if (Objects.nonNull(node.dynamicAgent())) {
                events.append(run.getRunId(), child.getStepId(), CreativeEventType.DYNAMIC_AGENT_CREATED,
                        CreativeEventSources.RUNTIME, plan.getPlanId(), new DynamicCreation(child.getStepId(), node.dynamicAgent(),
                                node.instruction(), blueprint.fingerprint(), blueprint.capabilities(), blueprint.maxTurns()));
            }
        }
        CreativeStep join = newStep(parent, plan, CollaborationToolContract.JOIN_NODE_KEY, CreativeStepType.JOIN);
        join.setInputJson(codec.write(Map.of()));
        steps.create(join);
        created(join);
        for (var node : frozen.request().nodes()) {
            for (String predecessor : node.dependsOn()) edge(run.getRunId(), nodes.get(predecessor).getStepId(), nodes.get(node.key()).getStepId());
            boolean sink = frozen.request().nodes().stream().noneMatch(other -> other.dependsOn().contains(node.key()));
            if (sink) edge(run.getRunId(), nodes.get(node.key()).getStepId(), join.getStepId());
        }
        edge(run.getRunId(), join.getStepId(), parent.getStepId());
        parent.setDelegationRound(plan.getRoundNo());
        graph.updateMetadata(parent);
        transition(parent, CreativeStepStatus.WAITING_CHILDREN, null, null, null, null);
        sessions.retireSession(parent.getStateSessionId());
        plan.setJoinStepId(join.getStepId());
        plan.setStatus(DelegationStatus.MATERIALIZED);
        graph.update(plan);
        events.append(run.getRunId(), parent.getStepId(), CreativeEventType.PLAN_CREATED,
                CreativeEventSources.RUNTIME, plan.getPlanId(), new PlanReceipt(plan.getPlanId(), plan.getTitle(), nodes.size(), plan.getRoundNo()));
        reconcile(run.getRunId());
        log.info("Creative collaboration materialized runId={} parentStepId={} planId={} workers={} round={}",
                run.getRunId(), parent.getStepId(), plan.getPlanId(), nodes.size(), plan.getRoundNo());
        return true;
    }

    /** 在完成或失败提交的同一事务中推进后继节点；事务提交后立即调度。 */
    @EventListener
    @Transactional
    public void settled(CreativeStepSettled signal) { reconcile(signal.runId()); }

    /** 用户取消或 Run 终态时关闭未决计划；Step 取消由 Run 聚合统一传播。 */
    @EventListener
    @Transactional
    public void stopped(CreativeRunStopped signal) {
        runs.lockByRunId(signal.runId());
        for (var plan : graph.plans(signal.runId())) {
            if (plan.getStatus() != DelegationStatus.DRAFT && plan.getStatus() != DelegationStatus.MATERIALIZED) continue;
            plan.setStatus(DelegationStatus.CANCELLED);
            graph.update(plan);
        }
    }

    /** 可重复执行的图补偿；扫描只负责丢失唤醒、进程重启和过期检查。 */
    @Transactional
    public void reconcile(String runId) {
        CreativeRun run = runs.lockByRunId(runId);
        if (Objects.isNull(run) || run.getStatus().isTerminal() || run.getStatus() == CreativeRunStatus.CANCEL_REQUESTED) return;
        var plans = graph.plans(runId);
        if (plans.stream().noneMatch(plan -> plan.getStatus() == DelegationStatus.MATERIALIZED)) return;
        var current = steps.findByRunId(runId);
        boolean failed = current.stream().anyMatch(step -> step.getStatus() == CreativeStepStatus.FAILED
                || step.getStatus() == CreativeStepStatus.CANCELLED || step.getStatus() == CreativeStepStatus.SKIPPED);
        if (failed || !run.getDeadlineAt().isAfter(LocalDateTime.now())) {
            failRun(run, current, plans);
            return;
        }
        var edges = graph.dependencies(runId);
        // 一次事务内达到固定点，最多经过有限个节点，不进行模型调用或外部 IO。
        boolean changed;
        do {
            changed = false;
            Map<String, CreativeStep> indexed = new HashMap<>();
            current.forEach(step -> indexed.put(step.getStepId(), step));
            for (CreativeStep step : current) {
                if (step.getStatus() != CreativeStepStatus.PENDING) continue;
                var incoming = edges.stream().filter(edge -> edge.getDownstreamStepId().equals(step.getStepId())).toList();
                if (incoming.stream().anyMatch(edge -> indexed.get(edge.getUpstreamStepId()).getStatus() != CreativeStepStatus.COMPLETED)) continue;
                if (step.getStepType() == CreativeStepType.JOIN) {
                    transition(step, CreativeStepStatus.COMPLETED, null, null, codec.write(new JoinResult(JoinPolicy.ALL_SUCCESS)), null);
                    events.append(runId, step.getStepId(), CreativeEventType.STEP_COMPLETED, CreativeEventSources.RUNTIME,
                            null, new StepCompletedEventPayload(step.getStepId(), step.getOwnerEpoch()));
                } else {
                    ready(run, step, step.getStateSessionId(), null);
                }
                changed = true;
            }
            if (changed) current = steps.findByRunId(runId);
        } while (changed);
        for (CreativeDelegation plan : plans) {
            if (plan.getStatus() != DelegationStatus.MATERIALIZED) continue;
            CreativeStep join = current.stream().filter(step -> step.getStepId().equals(plan.getJoinStepId())).findFirst().orElseThrow();
            if (join.getStatus() != CreativeStepStatus.COMPLETED) continue;
            CreativeStep parent = current.stream().filter(step -> step.getStepId().equals(plan.getParentStepId())).findFirst().orElseThrow();
            if (parent.getStatus() != CreativeStepStatus.WAITING_CHILDREN) continue;
            var summaries = summaries(current.stream().filter(step -> plan.getPlanId().equals(step.getDelegationPlanId())).toList());
            ready(run, parent, identifiers.nextStateSessionId(), codec.write(new Continuation(plan.getPlanId(), plan.getRoundNo(), summaries)));
            plan.setStatus(DelegationStatus.COMPLETED);
            graph.update(plan);
            log.info("Creative collaboration joined runId={} planId={} parentStepId={}", runId, plan.getPlanId(), parent.getStepId());
        }
        aggregation.aggregate(runId);
        // 容量不足时先前的 READY 信号可能未领取；任意步骤收敛后立即再次唤醒，不必等扫描。
        for (var ready : steps.findByRunId(runId)) if (ready.getStatus() == CreativeStepStatus.READY) {
            publisher.publishEvent(new CreativeDispatchSignal(ready.getStepId(), ready.getScheduleVersion()));
        }
    }

    /** 将持久化摘要注入下次独立调用，候选草稿通过结果工具按需读取。 */
    @Transactional(readOnly = true)
    public String instruction(ClaimedCreativeStep claim, String instruction) {
        CreativeStep step = steps.findByStepId(claim.stepId()).orElseThrow();
        List<ResultSummary> upstream = List.of();
        if (Objects.nonNull(step.getParentStepId())) {
            var ids = graph.dependencies(claim.runId()).stream().filter(edge -> edge.getDownstreamStepId().equals(step.getStepId()))
                    .map(CreativeStepDependency::getUpstreamStepId).toList();
            upstream = summaries(steps.findByRunId(claim.runId()).stream().filter(value -> ids.contains(value.getStepId())).toList());
        }
        if (Objects.isNull(step.getContinuationJson()) && upstream.isEmpty() && Objects.isNull(step.getParentStepId())) return instruction;
        var context = new ExecutionContext(Objects.nonNull(step.getParentStepId()),
                Objects.isNull(step.getContinuationJson()) ? null : codec.read(step.getContinuationJson(), Continuation.class), upstream);
        String json = codec.write(context);
        if (json.length() > CollaborationToolContract.CONTEXT_CHARACTERS) {
            throw new AgentRuntimeException(CreativeErrorCode.BUDGET_EXCEEDED, CollaborationMessages.BUDGET_EXCEEDED);
        }
        return instruction + CollaborationMessages.CONTEXT_TEMPLATE.formatted(json);
    }

    /** 不在摘要中拼接候选内容、提示词或工具参数。 */
    private List<ResultSummary> summaries(List<CreativeStep> selected) {
        return selected.stream().filter(step -> Objects.nonNull(step.getResultJson())).map(step -> {
            var result = codec.read(step.getResultJson(), CreativeStepResult.class);
            return new ResultSummary(step.getStepId(), step.getNodeKey(), result.kind(),
                    abbreviated(result.content(), CollaborationToolContract.SUMMARY_CHARACTERS), result.verdict(),
                    result.issues().stream().limit(CollaborationToolContract.SUMMARY_ISSUES)
                            .map(issue -> abbreviated(issue, CollaborationToolContract.ISSUE_CHARACTERS)).toList(),
                    result.artifactIds().stream().limit(CollaborationToolContract.SUMMARY_ARTIFACTS).toList());
        }).toList();
    }

    /** 截断时保留合法 UTF-16 字符，避免在代理对中间切开表情或特殊文字。 */
    private String abbreviated(String text, int limit) {
        if (text.length() <= limit) return text;
        int end = Character.isHighSurrogate(text.charAt(limit - 1)) ? limit - 1 : limit;
        return text.substring(0, end);
    }

    /** 失败不会伪装为用户取消；等待节点关闭，运行节点收到取消后自行释放资源。 */
    private void failRun(CreativeRun run, List<CreativeStep> current, List<CreativeDelegation> plans) {
        boolean hasFailed = current.stream().anyMatch(step -> step.getStatus() == CreativeStepStatus.FAILED);
        for (CreativeStep step : current) {
            if (step.getStatus().isTerminal() || step.getStatus() == CreativeStepStatus.RUNNING
                    || step.getStatus() == CreativeStepStatus.CANCEL_REQUESTED) continue;
            CreativeStepStatus target = !hasFailed && Objects.isNull(step.getParentStepId())
                    ? CreativeStepStatus.FAILED : CreativeStepStatus.SKIPPED;
            transition(step, target, null, null, null, CreativeErrorCode.DEPENDENCY_FAILED);
            sessions.retireSession(step.getStateSessionId());
            if (target == CreativeStepStatus.FAILED) hasFailed = true;
        }
        steps.requestCancelRunningByRunId(run.getRunId(), SystemActor.CREATIVE_RUNTIME.code());
        for (var plan : plans) if (plan.getStatus() == DelegationStatus.MATERIALIZED || plan.getStatus() == DelegationStatus.DRAFT) {
            plan.setStatus(DelegationStatus.FAILED);
            graph.update(plan);
        }
        aggregation.aggregate(run.getRunId());
    }

    /** 创建尚不可执行的节点，状态许可到 READY 时才登记，防止排队过久提前失效。 */
    private CreativeStep newStep(CreativeStep parent, CreativeDelegation plan, String key, CreativeStepType type) {
        String actor = SystemActor.CREATIVE_RUNTIME.code();
        return CreativeStep.builder().stepId(identifiers.nextStepId()).runId(parent.getRunId()).parentStepId(parent.getStepId())
                .stepType(type).status(CreativeStepStatus.PENDING).scheduleVersion(0L).ownerEpoch(0L)
                .attemptNo(0).maxAttempts(CollaborationToolContract.RETRY_ATTEMPTS).baseProjectRevision(parent.getBaseProjectRevision())
                .delegationPlanId(plan.getPlanId()).nodeKey(key).delegationDepth(Objects.requireNonNullElse(parent.getDelegationDepth(), 0) + 1)
                .delegationRound(0).reservedEpoch(-1L).createdBy(actor).updatedBy(actor).build();
    }

    /** 两端均由本次 Run 创建；父子归属和调度依赖保持独立。 */
    private void edge(String runId, String from, String to) {
        CreativeStepDependency edge = new CreativeStepDependency();
        edge.setRunId(runId); edge.setUpstreamStepId(from); edge.setDownstreamStepId(to); edge.setJoinPolicy(JoinPolicy.ALL_SUCCESS);
        edge.setCreatedBy(SystemActor.CREATIVE_RUNTIME.code()); edge.setUpdatedBy(SystemActor.CREATIVE_RUNTIME.code());
        graph.create(edge);
    }

    /** 将子节点创建记录纳入可回放事件日志。 */
    private void created(CreativeStep step) {
        events.append(step.getRunId(), step.getStepId(), CreativeEventType.STEP_CREATED, CreativeEventSources.RUNTIME,
                null, new StepCreatedEventPayload(step.getStepId(), step.getStepType(), step.getScheduleVersion()));
    }

    /** READY、许可登记和调度 Outbox 同事务提交；模型执行只发生在提交之后。 */
    private void ready(CreativeRun run, CreativeStep step, String sessionId, String continuation) {
        transition(step, CreativeStepStatus.READY, sessionId, continuation, null, null);
        sessions.register(run, sessionId);
        long version = step.getScheduleVersion() + 1;
        String actor = SystemActor.CREATIVE_RUNTIME.code();
        outbox.create(CreativeDispatchOutbox.builder().stepId(step.getStepId()).scheduleVersion(version)
                .status(CreativeDispatchOutboxStatus.PENDING).attempts(0).createdBy(actor).updatedBy(actor).build());
        publisher.publishEvent(new CreativeDispatchSignal(step.getStepId(), version));
    }

    /** Run 锁内撤销执行权并推进图状态，CAS 失败使整个图事务回滚。 */
    private void transition(CreativeStep step, CreativeStepStatus to, String session, String continuation, String output, CreativeErrorCode error) {
        int changed = transitions.transition(step.getStepId(), step.getStatus(), to, session, continuation, output,
                Objects.isNull(error) ? null : error.code(), Objects.isNull(error) ? null : CollaborationMessages.DEPENDENCY_FAILED,
                CollaborationToolContract.RETRY_ATTEMPTS, SystemActor.CREATIVE_RUNTIME.code());
        if (changed != 1) throw new IllegalStateException(CollaborationMessages.CHECKPOINT_CONFLICT);
    }

    /** 临时规格审计事件，完整 Blueprint 另存于计划和 Step。 */
    public record DynamicCreation(
            /** 独立执行步骤。 */ String stepId,
            /** 创建原因及原始纯数据配置。 */ DynamicAgentRequest request,
            /** 此次业务输入。 */ String instruction,
            /** 实际执行规格指纹。 */ String blueprintFingerprint,
            /** 父权限求交后的有效能力。 */ List<CapabilityManifest> capabilities,
            /** 实际轮数上限。 */ int maxTurns
    ) { }

    /** 父步骤恢复命令，仅包含当前轮次的结果引用和摘要。 */
    public record Continuation(
            /** 已汇合的计划。 */ String planId,
            /** 首次为一的计划轮数。 */ int round,
            /** 各 Worker 的摘要。 */ List<ResultSummary> results
    ) { }
    /** 有界的跨 Agent 上下文投影。 */
    public record ResultSummary(
            /** 可通过工具读取的步骤引用。 */ String stepId,
            /** 批内节点名。 */ String nodeKey,
            /** 结果 schema 类型。 */ StepResultKind kind,
            /** 短摘要。 */ String content,
            /** 仅评审提供的结论。 */ ReviewVerdict verdict,
            /** 具体问题列表。 */ List<String> issues,
            /** 平台产物引用。 */ List<Long> artifactIds
    ) { }
    private record ExecutionContext(
            /** 当前是否为必须提交结果的 Worker。 */ boolean worker,
            /** Director 汇合检查点。 */ Continuation continuation,
            /** 当前 Worker 的直接前置摘要。 */ List<ResultSummary> upstream
    ) { }
    private record JoinResult(/** 汇合策略。 */ JoinPolicy policy) { }
    private record PlanReceipt(
            /** 可审计计划标识。 */ String planId,
            /** 展示名称。 */ String title,
            /** Worker 数量。 */ int workers,
            /** 计划轮数。 */ int round
    ) { }
}
