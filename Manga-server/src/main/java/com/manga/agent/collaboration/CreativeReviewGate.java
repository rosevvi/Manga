package com.manga.agent.collaboration;

import com.manga.agent.changeset.ChangeSetCodec;
import com.manga.agent.changeset.ChangeSetKind;
import com.manga.agent.changeset.ScriptChangeSet;
import com.manga.agent.changeset.StoryboardChangeSet;
import com.fasterxml.jackson.databind.JsonNode;
import com.manga.agent.domain.CreativeErrorCode;
import com.manga.agent.domain.CreativeStepStatus;
import com.manga.agent.harness.AgentRuntimeException;
import com.manga.entity.CreativeStep;
import com.manga.repository.CreativeGraphRepository;
import com.manga.repository.CreativeStepRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.Comparator;
import java.util.Objects;

/** 最终提案前要求最近一轮协作已经汇合且连续性评审通过。 */
@Service
@RequiredArgsConstructor
public class CreativeReviewGate {
    private final CreativeGraphRepository graph;
    private final CreativeStepRepository steps;
    private final ChangeSetCodec codec;

    /** 执行器在最终完成前复核评审，防止修订失败后仅回复文本就宣告成功。 */
    @Transactional(readOnly = true)
    public void requirePassedStep(String stepId) { requirePassed(steps.findByStepId(stepId).orElseThrow()); }

    /** 已评审的候选必须原样进入提案，不能在评审后偷偷修改正文。 */
    @Transactional(readOnly = true)
    public void requireCandidate(CreativeStep parent, ChangeSetKind kind, JsonNode input) {
        requirePassed(parent);
        var latest = graph.plans(parent.getRunId()).stream()
                .filter(plan -> plan.getParentStepId().equals(parent.getStepId()))
                .max(Comparator.comparingInt(plan -> plan.getRoundNo())).orElse(null);
        if (Objects.isNull(latest) || kind == ChangeSetKind.IMAGE_GENERATION) return;
        StepResultKind expected = kind == ChangeSetKind.SCRIPT ? StepResultKind.SCRIPT_DRAFT : StepResultKind.STORYBOARD_DRAFT;
        String canonical = canonicalCandidate(kind, input);
        boolean matched = steps.findByRunId(parent.getRunId()).stream()
                .filter(step -> latest.getPlanId().equals(step.getDelegationPlanId()) && Objects.nonNull(step.getResultJson()))
                .map(step -> codec.read(step.getResultJson(), CreativeStepResult.class))
                .filter(result -> result.kind() == expected)
                .anyMatch(result -> canonical.equals(canonicalCandidate(kind, result.candidate())));
        if (!matched) denied();
    }

    /** 按领域 Schema 消除 JSON 字段顺序差异，保留正文和列表内容的精确一致性。 */
    private String canonicalCandidate(ChangeSetKind kind, JsonNode value) {
        return kind == ChangeSetKind.SCRIPT ? codec.write(codec.validate(value, ScriptChangeSet.class))
                : codec.write(codec.validate(value, StoryboardChangeSet.class));
    }

    /** 普通单 Agent 任务不要求评审；Worker 永远不能创建业务提案。 */
    @Transactional(readOnly = true)
    public void requirePassed(CreativeStep parent) {
        if (Objects.nonNull(parent.getParentStepId())) denied();
        var latest = graph.plans(parent.getRunId()).stream()
                .filter(plan -> plan.getParentStepId().equals(parent.getStepId()))
                .max(Comparator.comparingInt(plan -> plan.getRoundNo())).orElse(null);
        if (Objects.isNull(latest)) return;
        if (latest.getStatus() != DelegationStatus.COMPLETED) denied();
        boolean passed = steps.findByRunId(parent.getRunId()).stream()
                .filter(step -> latest.getPlanId().equals(step.getDelegationPlanId()))
                .filter(step -> step.getStatus() == CreativeStepStatus.COMPLETED && Objects.nonNull(step.getResultJson()))
                .map(step -> codec.read(step.getResultJson(), CreativeStepResult.class))
                .anyMatch(result -> result.kind() == StepResultKind.REVIEW && result.verdict() == ReviewVerdict.PASS);
        if (!passed) denied();
    }

    /** 评审未完成、未通过或候选被改写时停止业务提案。 */
    private void denied() { throw new AgentRuntimeException(CreativeErrorCode.REVIEW_REQUIRED, CollaborationMessages.REVIEW_REQUIRED); }
}
