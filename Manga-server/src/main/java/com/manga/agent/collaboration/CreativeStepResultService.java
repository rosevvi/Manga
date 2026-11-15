package com.manga.agent.collaboration;

import com.manga.agent.harness.ToolInputValidationException;
import com.manga.entity.CreativeStep;
import java.util.List;
import java.util.stream.Collectors;

import com.fasterxml.jackson.databind.JsonNode;
import com.manga.agent.blueprint.AgentBlueprintCodec;
import com.manga.agent.capability.AgentRuntimeContext;
import com.manga.agent.capability.CapabilitySummary;
import com.manga.agent.capability.CapabilityTarget;
import com.manga.agent.domain.SystemActor;
import com.manga.agent.changeset.ChangeSetCodec;
import com.manga.agent.changeset.ScriptChangeSet;
import com.manga.agent.changeset.StoryboardChangeSet;
import com.manga.agent.domain.CreativeErrorCode;
import com.manga.agent.domain.CreativeStepStatus;
import com.manga.agent.domain.CreativeOperationType;
import com.manga.agent.execution.ClaimedCreativeStep;
import com.manga.agent.harness.AgentRuntimeException;
import com.manga.agent.profile.AgentProfileKey;
import com.manga.repository.CreativeGraphRepository;
import com.manga.repository.CreativeRunRepository;
import com.manga.repository.CreativeStepRepository;
import com.manga.service.ProjectRevisionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/** 校验、冻结和授权读取 StepResult；Worker 不共享可变状态或内部 StateStore。 */
@Service
@RequiredArgsConstructor
public class CreativeStepResultService {
    private final CreativeRunRepository runs;
    private final CreativeStepRepository steps;
    private final CreativeGraphRepository graph;
    private final ChangeSetCodec codec;
    private final AgentBlueprintCodec blueprints;
    private final CollaborationProperties properties;
    private final ProjectRevisionService revisions;

    /** 一个 Worker 只能提交一份不可变结果，重复相同内容返回原结果。 */
    @Transactional
    public CreativeStepResult submit(JsonNode input, AgentRuntimeContext context) {
        var run = runs.lockByRunId(context.runId());
        var step = steps.findByStepId(context.stepId()).orElse(null);
        CollaborationGuard.requireRun(run, step, context);
        requireRepairSources(step);
        if (Objects.isNull(step.getParentStepId())) invalid();
        CreativeStepResult result = codec.validateForTool(input, CreativeStepResult.class);
        AgentProfileKey profile = blueprints.deserialize(step.getBlueprintSnapshotJson()).profileKey();
        StepResultKind expected = switch (profile) {
            case SCRIPT_PARSER -> StepResultKind.ANALYSIS;
            case SCRIPT_WRITER -> StepResultKind.SCRIPT_DRAFT;
            case STORYBOARD_DIRECTOR -> StepResultKind.STORYBOARD_DRAFT;
            case CONTINUITY_REVIEWER -> StepResultKind.REVIEW;
            default -> throw new AgentRuntimeException(CreativeErrorCode.STEP_RESULT_REQUIRED, CollaborationMessages.RESULT_REQUIRED);
        };
        if (result.kind() != expected) invalidField("kind", "本角色必须提交 " + expected.name());
        boolean hasCandidate = Objects.nonNull(result.candidate()) && !result.candidate().isNull();
        if ((expected == StepResultKind.REVIEW) != Objects.nonNull(result.verdict())) {
            invalidField("verdict", expected == StepResultKind.REVIEW ? "评审必须提供 PASS 或 NEEDS_REPAIR" : "非评审结果必须为 null");
        }
        if ((expected == StepResultKind.SCRIPT_DRAFT || expected == StepResultKind.STORYBOARD_DRAFT) != hasCandidate) {
            invalidField("candidate", hasCandidate ? "分析或评审不能提供候选" : "草稿必须提供对应候选结构");
        }
        if (result.verdict() == ReviewVerdict.NEEDS_REPAIR && result.issues().isEmpty()) {
            invalidField("issues", "需要修订时至少提供一个具体问题");
        }
        Long chapterId = null;
        if (expected == StepResultKind.SCRIPT_DRAFT) chapterId = codec.validateForTool(result.candidate(), ScriptChangeSet.class).chapterId();
        if (expected == StepResultKind.STORYBOARD_DRAFT) chapterId = codec.validateForTool(result.candidate(), StoryboardChangeSet.class).chapterId();
        if ((run.getOperationType() == CreativeOperationType.GENERATE_CHAPTER_STORYBOARD
                && expected == StepResultKind.STORYBOARD_DRAFT && !Objects.equals(chapterId, run.getTargetId()))
                || (run.getOperationType() == CreativeOperationType.GENERATE_SCRIPT_CHAPTER
                && expected == StepResultKind.SCRIPT_DRAFT && Objects.nonNull(chapterId))) invalid();
        if (Objects.nonNull(chapterId)) revisions.chapter(run.getProjectId(), chapterId);
        var artifactIds = new HashSet<>(result.artifactIds());
        if (artifactIds.size() != result.artifactIds().size()
                || !graph.artifactsBelongTo(artifactIds, run.getProjectId(), run.getUserId())) invalid();
        String json = codec.write(result);
        if (json.length() > properties.maxResultCharacters()) invalid();
        if (Objects.nonNull(step.getResultJson()) && !step.getResultJson().equals(json)) {
            throw new AgentRuntimeException(CreativeErrorCode.INVALID_REQUEST, CollaborationMessages.CHECKPOINT_CONFLICT);
        }
        if (Objects.isNull(step.getResultJson())) {
            if (!graph.saveResult(step.getStepId(), context.ownerInstanceId(), context.ownerEpoch(), json, SystemActor.STEP_EXECUTION.code())) {
                throw new AgentRuntimeException(CreativeErrorCode.OWNER_LEASE_LOST, CollaborationMessages.OWNER_LOST);
            }
        }
        return result;
    }

    /** 只有已完成的同 Run 结果可读；Worker 还必须位于调用者的传递前置依赖中。 */
    @Transactional
    public CreativeStepResult read(JsonNode input, AgentRuntimeContext context) {
        ReadRequest request = codec.validate(input, ReadRequest.class);
        var run = runs.findByRunId(context.runId()).orElse(null);
        var caller = steps.findByStepId(context.stepId()).orElse(null);
        CollaborationGuard.requireRun(run, caller, context);
        var target = steps.findByStepId(request.stepId()).orElse(null);
        if (Objects.isNull(target) || !target.getRunId().equals(context.runId())
                || target.getStatus() != CreativeStepStatus.COMPLETED || Objects.isNull(target.getResultJson())) denied();
        if (Objects.nonNull(caller.getParentStepId())) {
            Set<String> allowed = new HashSet<>();
            allowed.add(caller.getStepId());
            var edges = graph.dependencies(context.runId());
            boolean changed;
            do {
                changed = false;
                for (var edge : edges) if (allowed.contains(edge.getDownstreamStepId())) changed |= allowed.add(edge.getUpstreamStepId());
            } while (changed);
            allowed.remove(caller.getStepId());
            if (!allowed.contains(target.getStepId())) denied();
            if (!Objects.equals(caller.getDelegationPlanId(), target.getDelegationPlanId())) {
                runs.lockByRunId(context.runId());
                caller = steps.findByStepId(context.stepId()).orElseThrow();
                CollaborationGuard.requireOwner(caller, context.ownerInstanceId(), context.ownerEpoch(), context.stateSessionId());
                Set<String> read = readSources(caller);
                if (read.add(target.getStepId())) {
                    if (!graph.recordReads(caller.getStepId(), context.ownerInstanceId(), context.ownerEpoch(),
                            codec.write(read.stream().sorted().toList()), SystemActor.STEP_EXECUTION.code())) {
                        throw new AgentRuntimeException(CreativeErrorCode.OWNER_LEASE_LOST, CollaborationMessages.OWNER_LOST);
                    }
                }
            }
        }
        return codec.read(target.getResultJson(), CreativeStepResult.class);
    }

    /** 执行器用于工具结果之后停止流，以及提交结果后崩溃时跳过再次模型调用。 */
    @Transactional(readOnly = true)
    public String submitted(String stepId) { return steps.findByStepId(stepId).map(value -> value.getResultJson()).orElse(null); }

    /** 已授权读取后生成类型化摘要，清楚区分候选、分析与评审。 */
    @Transactional(readOnly = true)
    public CapabilitySummary describeRead(JsonNode input, AgentRuntimeContext context, CreativeStepResult result) {
        var request = codec.validateForTool(input, ReadRequest.class);
        var target = steps.findByStepId(request.stepId()).orElseThrow();
        int round = graph.plans(context.runId()).stream().filter(plan -> plan.getPlanId().equals(target.getDelegationPlanId()))
                .mapToInt(plan -> plan.getRoundNo()).findFirst().orElse(1);
        String kind = switch (result.kind()) {
            case ANALYSIS -> "剧本分析";
            case SCRIPT_DRAFT -> "章节草稿";
            case STORYBOARD_DRAFT -> "分镜草稿";
            case REVIEW -> "评审意见";
        };
        String title = "第 " + round + " 轮" + kind;
        return new CapabilitySummary(title, result.kind() == StepResultKind.REVIEW
                ? result.verdict().name() + " · " + result.issues().size() + " 项问题" : "已取得候选结果", "读取" + title,
                new CapabilityTarget(CapabilityTarget.Kind.STEP_RESULT, target.getStepId(), round));
    }

    /** 判断本次调用是否为只提交结果的独立 Worker。 */
    @Transactional(readOnly = true)
    public boolean isWorker(ClaimedCreativeStep claim) {
        return steps.findByStepId(claim.stepId()).map(value -> Objects.nonNull(value.getParentStepId())).orElse(false);
    }

    /** 修订提交之前必须实际读取直接引用的上一轮候选和评审，不能仅凭说明重新生成。 */
    private void requireRepairSources(CreativeStep step) {
        if (!RepairMessages.REPAIR_NODE.equals(step.getNodeKey())) { return; }
        var indexed = steps.findByRunId(step.getRunId()).stream().collect(Collectors.toMap(
                CreativeStep::getStepId, value -> value));
        var required = graph.dependencies(step.getRunId()).stream().filter(edge -> edge.getDownstreamStepId().equals(step.getStepId()))
                .map(edge -> indexed.get(edge.getUpstreamStepId())).filter(Objects::nonNull)
                .filter(source -> !Objects.equals(source.getDelegationPlanId(), step.getDelegationPlanId()))
                .map(CreativeStep::getStepId).toList();
        if (!readSources(step).containsAll(required)) {
            throw new ToolInputValidationException(List.of(
                    new ToolInputValidationException.FieldIssue("sourceResults",
                            "先通过 read_delegated_result 读取原候选和评审：" + String.join(", ", required))));
        }
    }

    /** 将数据库内的纯结果标识列表恢复为去重集合。 */
    private Set<String> readSources(CreativeStep step) {
        return Objects.isNull(step.getReadResultStepIdsJson()) ? new HashSet<>()
                : new HashSet<>(List.of(codec.read(step.getReadResultStepIdsJson(), String[].class)));
    }

    /** 输出 schema 或业务归属不合法时拒绝保存。 */
    private void invalid() { throw new AgentRuntimeException(CreativeErrorCode.STEP_RESULT_REQUIRED, CollaborationMessages.RESULT_REQUIRED); }
    /** 字段级领域约束也返回可修正的安全说明。 */
    private void invalidField(String field, String constraint) {
        throw new ToolInputValidationException(List.of(new ToolInputValidationException.FieldIssue(field, constraint)));
    }
    /** 拒绝泄露本次调用依赖范围以外的结果。 */
    private void denied() { throw new AgentRuntimeException(CreativeErrorCode.PERMISSION_DENIED, CollaborationMessages.RESULT_SCOPE_DENIED); }

    /** 结果读取工具的严格输入契约。 */
    public record ReadRequest(
            /** 已完成的依赖结果步骤。 */ @NotBlank @Size(max = 36) String stepId
    ) { }
}
