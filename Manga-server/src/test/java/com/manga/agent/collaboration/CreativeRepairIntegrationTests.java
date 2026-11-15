package com.manga.agent.collaboration;

import com.manga.agent.harness.AgentRuntimeException;
import java.io.IOException;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.manga.agent.CreativeAgentTestSupport;
import com.manga.agent.api.RepairDecisionRequest;
import com.manga.agent.application.CreativeProjectionDispatcher;
import com.manga.agent.blueprint.AgentBlueprint;
import com.manga.agent.blueprint.AgentBlueprintCodec;
import com.manga.agent.blueprint.AgentBlueprintFingerprintMaterial;
import com.manga.agent.blueprint.AgentKernelKey;
import com.manga.agent.capability.AgentRuntimeContext;
import com.manga.agent.domain.AgentPermissionPolicy;
import com.manga.agent.domain.CreativeOperationType;
import com.manga.agent.domain.CreativeResourceReference;
import com.manga.agent.domain.CreativeResourceType;
import com.manga.agent.domain.CreativeRunStatus;
import com.manga.agent.domain.CreativeStepInput;
import com.manga.agent.domain.CreativeStepStatus;
import com.manga.agent.domain.CreativeStepType;
import com.manga.agent.domain.CreativeWorkflowType;
import com.manga.agent.execution.CreativeStepClaimService;
import com.manga.agent.execution.CreativeStepDispatcher;
import com.manga.agent.harness.MangaAgentInvoker;
import com.manga.agent.harness.ToolInputValidationException;
import com.manga.agent.profile.AgentProfileKey;
import com.manga.agent.state.AgentStateSessionService;
import com.manga.entity.CreativeDelegation;
import com.manga.entity.CreativeRun;
import com.manga.entity.CreativeStep;
import com.manga.entity.CreativeStepDependency;
import com.manga.repository.CreativeGraphRepository;
import com.manga.repository.CreativeRepairDecisionRepository;
import com.manga.repository.CreativeRunRepository;
import com.manga.repository.CreativeStepRepository;
import com.manga.service.TokenService;
import com.manga.common.enums.UserRole;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import io.agentscope.core.state.AgentState;
import io.agentscope.core.state.AgentStateStore;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import org.springframework.http.MediaType;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 隔离数据库验证确认前不推理、来源读取、并发幂等、计时恢复、冲突和到期收敛。 */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:manga_repair;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE")
@ActiveProfiles("test")
@AutoConfigureMockMvc
class CreativeRepairIntegrationTests {
    private static final long USER_ID = 1L;
    private static final String ACTOR = "test:repair";
    @MockitoBean private CreativeStepDispatcher dispatcher;
    @MockitoBean private CreativeProjectionDispatcher projectionDispatcher;
    @MockitoBean private MangaAgentInvoker invoker;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private ObjectMapper mapper;
    @Autowired private AgentBlueprintCodec blueprints;
    @Autowired private CreativeGraphRepository graph;
    @Autowired private CreativeGraphService graphs;
    @Autowired private CreativeRunBudgetService budgets;
    @Autowired private CreativeRunRepository runs;
    @Autowired private CreativeStepRepository steps;
    @Autowired private CreativeRepairDecisionRepository decisions;
    @Autowired private CreativeRepairDecisionService service;
    @Autowired private CreativeStepResultService results;
    @Autowired private CreativeStepClaimService claims;
    @Autowired private AgentStateSessionService sessions;
    @Autowired private PlatformTransactionManager transactions;
    @Autowired private TokenService tokens;
    @Autowired private MockMvc mvc;
    @Autowired private AgentStateStore stateStore;

    /** 同一轮多次汇合只产生一份确认单，执行资源所有权已撤销。 */
    @Test
    void failedReviewPausesWithoutSchedulingOrModelCalls() {
        var fixture = fixture(2);
        graphs.reconcile(fixture.runId());
        graphs.reconcile(fixture.runId());
        var parent = steps.findByStepId(fixture.parentId()).orElseThrow();
        assertThat(parent.getStatus()).isEqualTo(CreativeStepStatus.WAITING_REPAIR_CONFIRMATION);
        assertThat(parent.getOwnerInstanceId()).isNull();
        assertThat(parent.getLeaseUntil()).isNull();
        assertThat(runs.findByRunId(fixture.runId()).orElseThrow().getStatus()).isEqualTo(CreativeRunStatus.WAITING);
        assertThat(decisions.byRun(fixture.runId())).hasSize(1);
        assertThat(decisions.byRun(fixture.runId()).getFirst().getRemainingExecutionMillis()).isPositive();
        assertThatThrownBy(() -> stateStore.save("1", parent.getStateSessionId(), "director",
                AgentState.builder().userId("1").sessionId(parent.getStateSessionId()).summary("late state").build()))
                .isInstanceOf(AgentRuntimeException.class);
        verifyNoInteractions(invoker);
    }

    /** 并发确认只物化一轮，冻结模型和版本不变，提交必须先读旧候选与问题。 */
    @Test
    void concurrentConfirmationResumesFrozenWorkersAndRequiresBothSourceReads() throws Exception {
        var fixture = fixture(2);
        graphs.reconcile(fixture.runId());
        var decision = decisions.byRun(fixture.runId()).getFirst();
        decision.setPausedAt(LocalDateTime.now().minusHours(2));
        decision.setRemainingExecutionMillis(120_000L); decisions.update(decision);
        String oldSession = steps.findByStepId(fixture.parentId()).orElseThrow().getStateSessionId();
        var request = new RepairDecisionRequest(UUID.randomUUID().toString(), RepairAction.CONFIRM_REPAIR, "保留已有镜头结构");
        try (var workers = Executors.newVirtualThreadPerTaskExecutor()) {
            var first = workers.submit(() -> service.decide(fixture.runId(), decision.getDecisionId(), USER_ID, request));
            var second = workers.submit(() -> service.decide(fixture.runId(), decision.getDecisionId(), USER_ID, request));
            assertThat(first.get(5, TimeUnit.SECONDS).repairPlanId()).isEqualTo(second.get(5, TimeUnit.SECONDS).repairPlanId());
        }
        var updated = decisions.byRun(fixture.runId()).getFirst();
        assertThat(updated.getStatus()).isEqualTo(RepairDecisionStatus.CONFIRMED);
        assertThat(graph.plans(fixture.runId())).hasSize(2);
        var repair = steps.findByRunId(fixture.runId()).stream().filter(step -> RepairMessages.REPAIR_NODE.equals(step.getNodeKey())).findFirst().orElseThrow();
        assertThat(repair.getBlueprintSnapshotJson()).isEqualTo(steps.findByStepId(fixture.candidateId()).orElseThrow().getBlueprintSnapshotJson());
        assertThat(repair.getStateSessionId()).isNotEqualTo(oldSession);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM manga_storyboard_shot WHERE project_id = ?", Integer.class, fixture.projectId())).isZero();
        var claimed = claims.claim(repair.getStepId(), repair.getScheduleVersion()).orElseThrow();
        var run = runs.findByRunId(fixture.runId()).orElseThrow();
        assertThat(run.getDeadlineAt()).isAfter(LocalDateTime.now().plusSeconds(90)).isBefore(LocalDateTime.now().plusMinutes(2));
        var context = new AgentRuntimeContext(USER_ID, run.getProjectId(), null, run.getRunId(), claimed.stepId(), claimed.stateSessionId(),
                claimed.ownerInstanceId(), claimed.ownerEpoch(), run.getDeadlineAt().atZone(ZoneId.systemDefault()).toInstant(), AgentPermissionPolicy.READ_ONLY);
        var candidate = mapper.readTree(steps.findByStepId(fixture.candidateId()).orElseThrow().getResultJson());
        assertThatThrownBy(() -> results.submit(candidate, context)).isInstanceOf(ToolInputValidationException.class);
        results.read(mapper.valueToTree(new CreativeStepResultService.ReadRequest(fixture.candidateId())), context);
        results.read(mapper.valueToTree(new CreativeStepResultService.ReadRequest(fixture.reviewId())), context);
        assertThat(results.submit(candidate, context).kind()).isEqualTo(StepResultKind.STORYBOARD_DRAFT);
        assertThat(steps.findByStepId(repair.getStepId()).orElseThrow().getReadResultStepIdsJson())
                .contains(fixture.candidateId(), fixture.reviewId());
        var foreign = fixture(2);
        assertThatThrownBy(() -> results.read(mapper.valueToTree(new CreativeStepResultService.ReadRequest(foreign.candidateId())), context))
                .isInstanceOf(AgentRuntimeException.class);
        verifyNoInteractions(invoker);
    }

    /** 手动编辑后旧候选不能被继续修订，确认和领域版本检查在同一事务。 */
    @Test
    void changedProjectClosesConfirmationAsConflict() {
        var fixture = fixture(2); graphs.reconcile(fixture.runId());
        var decision = decisions.byRun(fixture.runId()).getFirst();
        jdbc.update("UPDATE manga_project SET revision = revision + 1 WHERE id = ?", fixture.projectId());
        var view = service.decide(fixture.runId(), decision.getDecisionId(), USER_ID,
                new RepairDecisionRequest(UUID.randomUUID().toString(), RepairAction.CONFIRM_REPAIR, null));
        assertThat(view.status()).isEqualTo(RepairDecisionStatus.CONFLICTED);
        assertThat(graph.plans(fixture.runId())).hasSize(1);
        assertThat(runs.findByRunId(fixture.runId()).orElseThrow().getStatus()).isEqualTo(CreativeRunStatus.FAILED);
    }

    /** 用户选择结束是幂等取消，不产生修订节点，也不删除旧候选。 */
    @Test
    void stopDecisionIsIdempotentAndDoesNotCreateRepairWorkers() {
        var fixture = fixture(2); graphs.reconcile(fixture.runId());
        var decision = decisions.byRun(fixture.runId()).getFirst();
        var request = new RepairDecisionRequest(UUID.randomUUID().toString(), RepairAction.STOP, null);
        assertThat(service.decide(fixture.runId(), decision.getDecisionId(), USER_ID, request).status()).isEqualTo(RepairDecisionStatus.STOPPED);
        assertThat(service.decide(fixture.runId(), decision.getDecisionId(), USER_ID, request).status()).isEqualTo(RepairDecisionStatus.STOPPED);
        assertThat(graph.plans(fixture.runId())).hasSize(1);
        assertThat(runs.findByRunId(fixture.runId()).orElseThrow().getStatus()).isEqualTo(CreativeRunStatus.CANCELLED);
        assertThat(steps.findByStepId(fixture.candidateId()).orElseThrow().getResultJson()).isNotBlank();
        verifyNoInteractions(invoker);
    }

    /** 等待过期后取消任务，重复扫描不删除草稿或重复创建新计划。 */
    @Test
    void expiredConfirmationCancelsAndRetainsCandidate() {
        var fixture = fixture(2); graphs.reconcile(fixture.runId());
        var decision = decisions.byRun(fixture.runId()).getFirst();
        decision.setExpiresAt(LocalDateTime.now().minusSeconds(1)); decisions.update(decision);
        service.expire(fixture.runId(), decision.getDecisionId());
        service.expire(fixture.runId(), decision.getDecisionId());
        assertThat(decisions.byRun(fixture.runId()).getFirst().getStatus()).isEqualTo(RepairDecisionStatus.EXPIRED);
        assertThat(runs.findByRunId(fixture.runId()).orElseThrow().getStatus()).isEqualTo(CreativeRunStatus.CANCELLED);
        assertThat(steps.findByStepId(fixture.candidateId()).orElseThrow().getResultJson()).isNotBlank();
    }

    /** 已冻结的零修订预算不能被确认或恢复绕过。 */
    @Test
    void exhaustedRepairBudgetEndsWithExplicitReason() {
        var fixture = fixture(0); graphs.reconcile(fixture.runId());
        var run = runs.findByRunId(fixture.runId()).orElseThrow();
        assertThat(run.getStatus()).isEqualTo(CreativeRunStatus.FAILED);
        assertThat(run.getErrorCode()).isEqualTo("REPAIR_BUDGET_EXHAUSTED");
        assertThat(decisions.byRun(fixture.runId())).isEmpty();
    }

    /** 预览接口严格校验所有权，其他已登录用户也无法读取候选。 */
    @Test
    void previewChecksOwner() throws Exception {
        var fixture = fixture(2); graphs.reconcile(fixture.runId());
        String token = tokens.issue(USER_ID, "repair-owner", "Owner", Set.of(UserRole.USER), null, false).accessToken();
        String other = tokens.issue(USER_ID + 999, "other-owner", "Other", Set.of(UserRole.USER), null, false).accessToken();
        String path = "/api/v1/creative-runs/" + fixture.runId() + "/repair-decisions";
        mvc.perform(get(path).header("Authorization", "Bearer " + token)).andExpect(status().isOk());
        mvc.perform(get(path).header("Authorization", "Bearer " + other)).andExpect(status().isForbidden());
        mvc.perform(get(path)).andExpect(status().isUnauthorized());
        mvc.perform(post(path + "/" + decisions.byRun(fixture.runId()).getFirst().getDecisionId() + "/decide")
                .header("Authorization", "Bearer " + other).contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(new RepairDecisionRequest(UUID.randomUUID().toString(), RepairAction.CONFIRM_REPAIR, null))))
                .andExpect(status().isForbidden());
    }

    /** 原子准备已完成候选和未通过评审，避免扫描器观察到半份图；不调用外部模型。 */
    private Fixture fixture(int maxRepairs) {
        return new TransactionTemplate(transactions).execute(tx -> {
            String name = "repair-" + UUID.randomUUID();
            jdbc.update("INSERT INTO manga_project(owner_user_id,name,revision,created_by,updated_by) VALUES(?,?,0,?,?)", USER_ID, name, ACTOR, ACTOR);
            long project = jdbc.queryForObject("SELECT id FROM manga_project WHERE name=?", Long.class, name);
            jdbc.update("INSERT INTO manga_project_script(project_id,title,chapter_count,created_by,updated_by) VALUES(?,?,1,?,?)", project, name, ACTOR, ACTOR);
            long script = jdbc.queryForObject("SELECT id FROM manga_project_script WHERE project_id=?", Long.class, project);
            jdbc.update("INSERT INTO manga_project_script_chapter(script_id,title,raw_content,sort_order,created_by,updated_by) VALUES(?,'章节','原始剧情',1,?,?)", script, ACTOR, ACTOR);
            long chapter = jdbc.queryForObject("SELECT id FROM manga_project_script_chapter WHERE script_id=?", Long.class, script);
            CreativeRun run = CreativeAgentTestSupport.run(USER_ID);
            run.setProjectId(project); run.setTargetType(CreativeResourceType.CHAPTER); run.setTargetId(chapter);
            run.setOperationType(CreativeOperationType.GENERATE_CHAPTER_STORYBOARD); run.setWorkflowType(CreativeWorkflowType.CHAPTER_STORYBOARD);
            run.setStatus(CreativeRunStatus.ACTIVE); runs.create(run);
            var budget = budgets.initialize(run.getRunId()); budget.setMaxRepairRounds(maxRepairs); graph.update(budget);
            var parent = step(run, null, null, "director", CreativeStepStatus.WAITING_CHILDREN, AgentProfileKey.MANGA_DIRECTOR, chapter);
            sessions.register(run, parent.getStateSessionId());
            var plan = new CreativeDelegation(); plan.setPlanId(UUID.randomUUID().toString()); plan.setRunId(run.getRunId());
            plan.setParentStepId(parent.getStepId()); plan.setTriggerToolCallId(UUID.randomUUID().toString()); plan.setOwnerEpoch(1L);
            plan.setRoundNo(1); plan.setTitle("第一轮候选"); plan.setStatus(DelegationStatus.MATERIALIZED);
            plan.setFrozenJson("{}"); plan.setRequestFingerprint("0".repeat(64)); plan.setCreatedBy(ACTOR); plan.setUpdatedBy(ACTOR); graph.create(plan);
            var draft = step(run, parent.getStepId(), plan.getPlanId(), "storyboard", CreativeStepStatus.COMPLETED, AgentProfileKey.STORYBOARD_DIRECTOR, chapter);
            var review = step(run, parent.getStepId(), plan.getPlanId(), "review", CreativeStepStatus.COMPLETED, AgentProfileKey.CONTINUITY_REVIEWER, chapter);
            var candidate = mapper.createObjectNode().put("chapterId", chapter);
            candidate.putArray("shots").addObject().put("title", "开场").put("sceneName", "庭院").put("shotType", "全景")
                    .put("cameraMovement", "推进").put("durationSeconds", 5).put("content", "角色进入庭院");
            draft.setResultJson(write(new CreativeStepResult(StepResultKind.STORYBOARD_DRAFT, "候选草稿", candidate, null, List.of(), List.of()))); graph.updateMetadata(draft);
            review.setResultJson(write(new CreativeStepResult(StepResultKind.REVIEW, "需要补充结尾", null, ReviewVerdict.NEEDS_REPAIR, List.of("补充结尾"), List.of()))); graph.updateMetadata(review);
            var join = step(run, parent.getStepId(), plan.getPlanId(), CollaborationToolContract.JOIN_NODE_KEY, CreativeStepStatus.PENDING, null, chapter);
            plan.setJoinStepId(join.getStepId()); graph.update(plan);
            edge(run, draft, review); edge(run, review, join); edge(run, join, parent);
            return new Fixture(run.getRunId(), parent.getStepId(), draft.getStepId(), review.getStepId(), project);
        });
    }

    /** 创建满足数据库快照配对约束的测试步骤，Worker 固定到版本九。 */
    private CreativeStep step(CreativeRun run, String parent, String plan, String key, CreativeStepStatus status, AgentProfileKey role, long chapter) {
        var value = CreativeStep.builder().stepId(UUID.randomUUID().toString()).runId(run.getRunId()).parentStepId(parent)
                .delegationPlanId(plan).nodeKey(key).delegationDepth(parent == null ? 0 : 1).delegationRound(1)
                .stepType(role == null ? CreativeStepType.JOIN : CreativeStepType.AGENT_INVOCATION).status(status)
                .scheduleVersion(1L).ownerEpoch(1L).attemptNo(0).maxAttempts(3).baseProjectRevision(0L).reservedEpoch(-1L)
                .inputJson(write(new CreativeStepInput("修订测试", CreativeOperationType.GENERATE_CHAPTER_STORYBOARD,
                        new CreativeResourceReference(CreativeResourceType.CHAPTER, chapter))))
                .createdBy(ACTOR).updatedBy(ACTOR).build();
        if (role != null) {
            var original = CreativeAgentTestSupport.blueprint().blueprint();
            var material = new AgentBlueprintFingerprintMaterial(role, 9L, original.model(), "冻结的角色提示词", Map.of(),
                    List.of(), AgentPermissionPolicy.READ_ONLY, 8);
            String fingerprint = blueprints.fingerprint(material);
            value.setBlueprintSnapshotJson(blueprints.serialize(new AgentBlueprint(new AgentKernelKey(fingerprint), role, 9L,
                    original.model(), material.systemPrompt(), Map.of(), List.of(), AgentPermissionPolicy.READ_ONLY, 8, fingerprint)));
            value.setBlueprintFingerprint(fingerprint); value.setStateSessionId(UUID.randomUUID().toString());
        }
        return steps.create(value);
    }
    /** 测试使用真实依赖边，权限校验与生产共享图定义。 */
    private void edge(CreativeRun run, CreativeStep from, CreativeStep to) {
        var edge = new CreativeStepDependency(); edge.setRunId(run.getRunId()); edge.setUpstreamStepId(from.getStepId());
        edge.setDownstreamStepId(to.getStepId()); edge.setJoinPolicy(JoinPolicy.ALL_SUCCESS); edge.setCreatedBy(ACTOR); edge.setUpdatedBy(ACTOR); graph.create(edge);
    }
    /** 测试已知数据的序列化错误直接使准备失败。 */
    private String write(Object value) {
        try { return mapper.writeValueAsString(value); }
        catch (IOException failure) { throw new IllegalStateException(failure); }
    }
    /** 一份来源明确的已完成评审场景。 */
    private record Fixture(/** 运行。 */ String runId, /** 父步骤。 */ String parentId,
            /** 候选引用。 */ String candidateId, /** 评审引用。 */ String reviewId, /** 项目。 */ long projectId) { }
}
