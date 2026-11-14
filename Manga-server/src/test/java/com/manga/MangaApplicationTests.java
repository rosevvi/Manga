package com.manga;

import com.manga.agent.CreativeAgentTestSupport;
import com.manga.agent.application.CancelCreativeRunCommand;
import com.manga.agent.application.CreativeRunStartResult;
import com.manga.agent.application.CreativeRuntime;
import com.manga.agent.application.StartCreativeRunCommand;
import com.manga.agent.blueprint.AgentBlueprintCompiler;
import com.manga.agent.domain.CreativeRunStatus;
import com.manga.agent.domain.CreativeOperationType;
import com.manga.agent.domain.CreativeResourceType;
import com.manga.agent.domain.CreativeResourceReference;
import com.manga.agent.domain.AgentPermissionPolicy;
import com.manga.agent.changeset.ChangeSetApprovalService;
import com.manga.agent.changeset.ChangeSetProposalService;
import com.manga.agent.changeset.ChangeSetKind;
import com.manga.agent.capability.AgentRuntimeContext;
import com.manga.agent.changeset.ApprovalDecision;
import com.manga.agent.changeset.ApprovalStatus;
import com.manga.agent.generation.AgnesVideoProvider;
import com.manga.agent.generation.GenerationJobQueryService;
import com.manga.common.enums.AiModelType;
import com.manga.agent.generation.GenerationJobStatus;
import com.manga.agent.generation.GenerationSnapshot;
import com.manga.agent.generation.GenerationSubmission;
import com.manga.service.security.SecretCipher;
import com.manga.service.TokenService;
import com.manga.common.enums.UserRole;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Set;
import org.springframework.http.MediaType;
import com.manga.agent.harness.MangaAgentInvoker;
import com.manga.agent.state.AgentStateSessionService;
import com.manga.entity.CreativeRun;
import com.manga.repository.CreativeRunRepository;
import io.agentscope.core.event.AgentEvent;
import io.agentscope.core.event.TextBlockDeltaEvent;
import io.agentscope.core.event.ToolResultStartEvent;
import io.agentscope.core.event.ToolResultEndEvent;
import io.agentscope.core.message.ToolResultState;
import java.util.concurrent.atomic.AtomicBoolean;
import io.agentscope.core.state.AgentState;
import io.agentscope.core.state.AgentStateStore;
import io.agentscope.core.state.VersionedState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doReturn;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 验证应用上下文能够正常创建。
 */
@SpringBootTest(properties = {"manga.creative.generation.poll-interval=50ms", "manga.upload.directory=target/test-video-media",
        "spring.datasource.url=jdbc:h2:mem:manga_application;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE"})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MangaApplicationTests {
    @MockitoBean
    private AgentBlueprintCompiler blueprintCompiler;
    @MockitoBean
    private MangaAgentInvoker invoker;
    @MockitoSpyBean
    private AgnesVideoProvider agnes;
    @Autowired
    private CreativeRunRepository runRepository;
    @Autowired
    private AgentStateSessionService stateSessions;
    @Autowired
    private ChangeSetApprovalService changeSetApprovals;
    @Autowired
    private SecretCipher secretCipher;
    @Autowired
    private TokenService tokenService;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private GenerationJobQueryService generationQueries;
    @Autowired
    private ChangeSetProposalService proposals;

    /** 模型边界模拟提案工具，真实验证文本与工具落库、等待审批、幂等应用和原规格恢复。 */
    @Test
    void storyboardExecutionPausesBeforeWritingAndApprovalAppliesOnlyOnce() throws Exception {
        long userId = jdbcTemplate.queryForObject("SELECT MIN(id) FROM manga_user", Long.class);
        String name = "storyboard-flow-" + UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO manga_project (owner_user_id, name, created_by, updated_by) VALUES (?, ?, 'test', 'test')", userId, name);
        long projectId = jdbcTemplate.queryForObject("SELECT id FROM manga_project WHERE name = ?", Long.class, name);
        jdbcTemplate.update("INSERT INTO manga_project_script (project_id, title, chapter_count, created_by, updated_by) VALUES (?, ?, 1, 'test', 'test')", projectId, name);
        long scriptId = jdbcTemplate.queryForObject("SELECT id FROM manga_project_script WHERE project_id = ?", Long.class, projectId);
        jdbcTemplate.update("INSERT INTO manga_project_script_chapter (script_id, title, raw_content, sort_order, created_by, updated_by) VALUES (?, '第一章', '测试章节正文', 1, 'test', 'test')", scriptId);
        long chapterId = jdbcTemplate.queryForObject("SELECT id FROM manga_project_script_chapter WHERE script_id = ?", Long.class, scriptId);
        var candidate = objectMapper.readTree("""
                {"chapterId":%d,"shots":[{"title":"开场镜头","sceneName":"庭院","shotType":"全景",
                  "cameraMovement":"缓慢推进","durationSeconds":5,"content":"角色走入庭院"}]}
                """.formatted(chapterId));
        AtomicBoolean proposed = new AtomicBoolean();
        when(invoker.invokeUntil(any(), any(), any(), any())).thenAnswer(invocation -> {
            AgentRuntimeContext context = invocation.getArgument(1);
            return Flux.defer(() -> {
                if (proposed.getAndSet(true)) return Flux.just((AgentEvent) new TextBlockDeltaEvent("reply", "block", "分镜已保存"));
                return Flux.just((AgentEvent) new TextBlockDeltaEvent("reply", "block", "开始生成分镜"),
                        new ToolResultStartEvent("reply", "proposal-tool", "propose_storyboard_change"))
                        .concatWith(Flux.defer(() -> {
                            proposals.propose(ChangeSetKind.STORYBOARD, candidate, context);
                            return Flux.just((AgentEvent) new ToolResultEndEvent("reply", "proposal-tool", "propose_storyboard_change", ToolResultState.SUCCESS));
                        }));
            });
        });
        var started = creativeRuntime.start(StartCreativeRunCommand.manual(userId, UUID.randomUUID().toString(), projectId,
                CreativeOperationType.GENERATE_CHAPTER_STORYBOARD, new CreativeResourceReference(CreativeResourceType.CHAPTER, chapterId),
                AgentPermissionPolicy.PROPOSE_CHANGES, "生成本章分镜"));
        awaitRunStatus(started.runId(), CreativeRunStatus.WAITING);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM manga_storyboard_shot WHERE project_id = ?", Integer.class, projectId)).isZero();
        assertThat(jdbcTemplate.queryForObject("SELECT lease_until FROM manga_creative_step WHERE run_id = ?", LocalDateTime.class, started.runId())).isNull();
        String originalBlueprint = jdbcTemplate.queryForObject("SELECT blueprint_snapshot_json FROM manga_creative_step WHERE run_id = ?", String.class, started.runId());
        String changeSet = jdbcTemplate.queryForObject("SELECT change_set_id FROM manga_creative_change_set WHERE run_id = ?", String.class, started.runId());
        changeSetApprovals.decide(changeSet, userId, ApprovalDecision.APPROVE, "采用这版分镜");
        changeSetApprovals.decide(changeSet, userId, ApprovalDecision.APPROVE, "重复点击");
        awaitRunStatus(started.runId(), CreativeRunStatus.COMPLETED);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM manga_storyboard_shot WHERE chapter_id = ?", Integer.class, chapterId)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("SELECT blueprint_snapshot_json FROM manga_creative_step WHERE run_id = ?", String.class, started.runId())).isEqualTo(originalBlueprint);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM manga_creative_event WHERE run_id = ? AND event_type = 'TOOL_COMPLETED'", Integer.class, started.runId())).isEqualTo(1);
    }

    /** 验证密钥必须显式查看、响应不缓存，查看后普通保存不会把遮罩写入密文。 */
    @Test
    void revealKeyChecksOwnershipAndPreservesSavedValue() throws Exception {
        long userId = jdbcTemplate.queryForObject("SELECT MIN(id) FROM manga_user", Long.class);
        String name = "key-display-" + UUID.randomUUID();
        String testSecret = "sk-test-only-not-a-real-credential";
        jdbcTemplate.update("""
                INSERT INTO manga_ai_provider_config (owner_user_id, name, provider_type, model_type, base_url,
                    default_model, api_key_ciphertext, enabled, default_config, created_by, updated_by)
                VALUES (?, ?, 'DEEPSEEK', 'TEXT', 'https://example.test', 'test-model', ?, TRUE, FALSE, 'test', 'test')
                """, userId, name, secretCipher.encrypt(testSecret));
        Long configId = jdbcTemplate.queryForObject("SELECT id FROM manga_ai_provider_config WHERE name = ?", Long.class, name);
        String token = tokenService.issue(userId, "admin", "Admin", Set.of(UserRole.ADMIN), null, false).accessToken();
        String path = "/api/v1/ai-provider-configs/" + configId;
        mockMvc.perform(post(path + "/reveal-key").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.data.apiKey").value(testSecret));
        String other = tokenService.issue(userId + 1000, "other", "Other", Set.of(UserRole.USER), null, false).accessToken();
        mockMvc.perform(post(path + "/reveal-key").header("Authorization", "Bearer " + other)).andExpect(status().isNotFound());
        mockMvc.perform(post(path + "/reveal-key")).andExpect(status().isUnauthorized());
        mockMvc.perform(put(path).header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"%s","providerType":"DEEPSEEK","modelType":"TEXT","baseUrl":"https://example.test",
                                 "defaultModel":"test-model","apiKey":null,"proxyType":"NONE","enabled":true,"defaultConfig":false}
                                """.formatted(name))).andExpect(status().isOk());
        String ciphertext = jdbcTemplate.queryForObject("SELECT api_key_ciphertext FROM manga_ai_provider_config WHERE id = ?", String.class, configId);
        assertThat(secretCipher.decrypt(ciphertext)).isEqualTo(testSecret);
    }

    /** 时间线分页重复读取稳定，并且不能读取其他用户的执行记录。 */
    @Test
    void timelineRestoresOrderedHistoryAndChecksOwnership() throws Exception {
        long userId = jdbcTemplate.queryForObject("SELECT MIN(id) FROM manga_user", Long.class);
        var started = creativeRuntime.start(StartCreativeRunCommand.conversation(userId,
                UUID.randomUUID().toString(), null, null, "验证安全时间线"));
        awaitRunStatus(started.runId(), CreativeRunStatus.COMPLETED);
        String token = tokenService.issue(userId, "admin", "Admin", Set.of(UserRole.ADMIN), null, false).accessToken();
        String path = "/api/v1/creative-runs/" + started.runId() + "/timeline";
        String first = mockMvc.perform(get(path).param("limit", "2").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.hasMore").value(true))
                .andReturn().getResponse().getContentAsString();
        String repeated = mockMvc.perform(get(path).param("limit", "2").header("Authorization", "Bearer " + token))
                .andReturn().getResponse().getContentAsString();
        assertThat(objectMapper.readTree(repeated).get("data")).isEqualTo(objectMapper.readTree(first).get("data"));
        long cursor = objectMapper.readTree(first).path("data").path("throughSequence").asLong();
        String second = mockMvc.perform(get(path).param("afterSequence", String.valueOf(cursor)).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(objectMapper.readTree(second).path("data").path("items").get(0).path("sequence").asLong()).isGreaterThan(cursor);
        assertThat(second).doesNotContain("systemPrompt", "blueprintSnapshot", "apiKey");
        String other = tokenService.issue(userId + 1000, "other", "Other", Set.of(UserRole.USER), null, false).accessToken();
        mockMvc.perform(get(path).header("Authorization", "Bearer " + other)).andExpect(status().isForbidden());
    }

    /** 只替换外部模型边界，受理、投影、数据库和执行器仍使用真实实现。 */
    @BeforeEach
    void prepareModelBoundary() {
        when(blueprintCompiler.compile(any()))
                .thenReturn(CreativeAgentTestSupport.blueprint());
        when(invoker.invokeUntil(any(),
                any(), any(), any()))
                .thenReturn(Flux.just(
                        new TextBlockDeltaEvent("reply", "block", "测试回答"))
                        .delayElements(Duration.ofMillis(150)).cast(AgentEvent.class));
    }

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CreativeRuntime creativeRuntime;

    @Autowired
    private AgentStateStore agentStateStore;

    /**
     * 确认基础 Bean 配置不存在启动错误。
     */
    @Test
    void contextLoads() {
    }

    @Test
    void flywayCreatesCreativeRunTable() {
        Integer tableCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES WHERE LOWER(TABLE_NAME) = 'manga_creative_run'",
                Integer.class);
        Integer migrationCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM flyway_schema_history WHERE success = TRUE", Integer.class);
        Integer triggerColumnCount = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM INFORMATION_SCHEMA.COLUMNS
                WHERE LOWER(TABLE_NAME) = 'manga_creative_run'
                  AND LOWER(COLUMN_NAME) IN ('trigger_source', 'operation_type', 'target_type', 'target_id')
                """, Integer.class);
        Integer contractColumnCount = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM INFORMATION_SCHEMA.COLUMNS
                WHERE LOWER(TABLE_NAME) = 'manga_creative_run'
                  AND LOWER(COLUMN_NAME) IN (
                    'request_id', 'engine_version', 'workflow_type', 'workflow_version',
                    'request_fingerprint', 'permission_policy', 'terminal_sequence', 'cancel_requested_at'
                  )
                """, Integer.class);
        String conversationNullable = jdbcTemplate.queryForObject("""
                SELECT IS_NULLABLE
                FROM INFORMATION_SCHEMA.COLUMNS
                WHERE LOWER(TABLE_NAME) = 'manga_creative_run'
                  AND LOWER(COLUMN_NAME) = 'conversation_id'
                """, String.class);

        assertThat(tableCount).isEqualTo(1);
        assertThat(migrationCount).isGreaterThanOrEqualTo(4);
        assertThat(triggerColumnCount).isEqualTo(4);
        assertThat(contractColumnCount).isEqualTo(8);
        assertThat(conversationNullable).isEqualTo("YES");
    }

    /**
     * 确认阶段 0 创作运行表全部创建，并具有完整审计字段和状态约束。
     */
    @Test
    void flywayCreatesCreativeRuntimeContractTables() {
        List<String> expectedTables = List.of(
                "manga_creative_conversation",
                "manga_creative_message",
                "manga_creative_run",
                "manga_creative_step",
                "manga_creative_dispatch_outbox",
                "manga_creative_change_set",
                "manga_creative_approval",
                "manga_creative_event"
        );
        List<String> actualTables = jdbcTemplate.queryForList("""
                SELECT LOWER(TABLE_NAME)
                FROM INFORMATION_SCHEMA.TABLES
                WHERE LOWER(TABLE_NAME) LIKE 'manga_creative_%'
                """, String.class);

        assertThat(actualTables).containsAll(expectedTables);
        for (String tableName : expectedTables) {
            Integer auditColumnCount = jdbcTemplate.queryForObject("""
                    SELECT COUNT(*)
                    FROM INFORMATION_SCHEMA.COLUMNS
                    WHERE LOWER(TABLE_NAME) = ?
                      AND LOWER(COLUMN_NAME) IN ('created_at', 'updated_at', 'created_by', 'updated_by')
                    """, Integer.class, tableName);
            assertThat(auditColumnCount).as("audit columns of %s", tableName).isEqualTo(4);
        }

        Integer checkConstraintCount = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM INFORMATION_SCHEMA.TABLE_CONSTRAINTS
                WHERE LOWER(TABLE_NAME) = 'manga_creative_run'
                  AND CONSTRAINT_TYPE = 'CHECK'
                """, Integer.class);
        assertThat(checkConstraintCount).isGreaterThanOrEqualTo(4);
    }

    /** 验证拒绝与审批后轮询、MP4 转存和 Run 恢复。 */
    @Test
    void videoProposalAndJobCompleteWithoutUsingTextModel() throws Exception {
        Long userId = jdbcTemplate.queryForObject("SELECT MIN(id) FROM manga_user", Long.class);
        String name = "video-test-" + UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO manga_project (owner_user_id, name, created_by, updated_by) VALUES (?, ?, 'test', 'test')",
                userId, name);
        Long projectId = jdbcTemplate.queryForObject("SELECT id FROM manga_project WHERE name = ?", Long.class, name);
        jdbcTemplate.update("""
                INSERT INTO manga_storyboard_shot (project_id, sort_order, shot_number, title, created_by, updated_by)
                VALUES (?, 1, '001', 'Test shot', 'test', 'test')
                """, projectId);
        Long shotId = jdbcTemplate.queryForObject("SELECT id FROM manga_storyboard_shot WHERE project_id = ?", Long.class, projectId);
        jdbcTemplate.update("""
                INSERT INTO manga_ai_provider_config (owner_user_id, name, provider_type, model_type, base_url,
                    default_model, api_key_ciphertext, enabled, default_config, created_by, updated_by)
                VALUES (?, ?, 'AGNES', 'VIDEO', 'https://apihub.agnes-ai.com',
                    'agnes-video-2.5-flash', ?, TRUE, FALSE, 'test', 'test')
                """, userId, name, secretCipher.encrypt("test-only"));
        Long configId = jdbcTemplate.queryForObject("SELECT id FROM manga_ai_provider_config WHERE name = ?", Long.class, name);

        String input = """
                {"shotId":%d,"configId":%d,"prompt":"A slow pan","seconds":5,"aspectRatio":"9:16"}
                """.formatted(shotId, configId);
        var run = startVideo(userId, projectId, shotId, input);
        awaitRunStatus(run.runId(), CreativeRunStatus.WAITING);
        assertThat(jdbcTemplate.queryForObject("SELECT state_session_id FROM manga_creative_step WHERE run_id = ?",
                String.class, run.runId())).isNull();
        String proposalId = jdbcTemplate.queryForObject("SELECT change_set_id FROM manga_creative_change_set WHERE run_id = ?",
                String.class, run.runId());
        assertThat(changeSetApprovals.decide(proposalId, userId, ApprovalDecision.REJECT, "test").status())
                .isEqualTo(ApprovalStatus.REJECTED);
        awaitRunStatus(run.runId(), CreativeRunStatus.COMPLETED);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM manga_generation_job WHERE run_id = ?",
                Integer.class, run.runId())).isZero();

        String resultUrl = "https://platform-outputs.agnes-ai.space/video.mp4";
        doReturn(new GenerationSubmission("video-test", new GenerationSnapshot(GenerationJobStatus.SUBMITTED, 0, null, null)))
                .when(agnes).submit(any());
        doReturn(new GenerationSnapshot(GenerationJobStatus.MATERIALIZING, 100, null, null, resultUrl))
                .when(agnes).query(any());
        doReturn(new byte[]{0, 0, 0, 16, 'f', 't', 'y', 'p', 'i', 's', 'o', 'm', 0, 0, 0, 0})
                .when(agnes).download(any());
        var approved = startVideo(userId, projectId, shotId, input);
        awaitRunStatus(approved.runId(), CreativeRunStatus.WAITING);
        String approvedProposal = jdbcTemplate.queryForObject("SELECT change_set_id FROM manga_creative_change_set WHERE run_id = ?",
                String.class, approved.runId());
        assertThat(changeSetApprovals.decide(approvedProposal, userId, ApprovalDecision.APPROVE, null).status())
                .isEqualTo(ApprovalStatus.APPROVED);
        awaitRunStatus(approved.runId(), CreativeRunStatus.COMPLETED, 12000L);
        assertThat(jdbcTemplate.queryForObject("SELECT media_type FROM manga_creative_artifact WHERE run_id = ?",
                String.class, approved.runId())).isEqualTo("video/mp4");
        assertThat(jdbcTemplate.queryForObject("SELECT result_url FROM manga_generation_job WHERE run_id = ?",
                String.class, approved.runId())).isNull();
        var videos = generationQueries.list(userId, projectId, null, null, null, shotId, AiModelType.VIDEO);
        assertThat(videos).hasSize(1);
        assertThat(generationQueries.list(userId, projectId, null, null, null, shotId, AiModelType.IMAGE)).isEmpty();
        assertThat(generationQueries.list(userId, projectId, null, null, null, shotId + 1000, AiModelType.VIDEO)).isEmpty();
        String token = tokenService.issue(userId, "admin", "Admin", Set.of(UserRole.ADMIN), null, false).accessToken();
        mockMvc.perform(get("/api/v1/generation-jobs/" + videos.getFirst().jobId() + "/download")
                        .header("Authorization", "Bearer " + token)).andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store")).andExpect(jsonPath("$.data.url").isNotEmpty());
    }

    /** 使用同一个镜头和表单参数启动独立测试 Run。 */
    private CreativeRunStartResult startVideo(long userId, long projectId, long shotId, String input) {
        return creativeRuntime.start(StartCreativeRunCommand.manual(userId, UUID.randomUUID().toString(), projectId,
                CreativeOperationType.GENERATE_SHOT_VIDEO, new CreativeResourceReference(CreativeResourceType.STORYBOARD_SHOT, shotId),
                AgentPermissionPolicy.PROPOSE_CHANGES, input));
    }

    /**
     * 确认阶段二 Blueprint 快照字段和 AgentScope 状态表已由 Flyway 创建。
     */
    @Test
    void flywayCreatesAgentRuntimeStateContract() {
        Integer stateTableCount = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM INFORMATION_SCHEMA.TABLES
                WHERE LOWER(TABLE_NAME) = 'manga_agent_state'
                """, Integer.class);
        Integer stepSnapshotColumnCount = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM INFORMATION_SCHEMA.COLUMNS
                WHERE LOWER(TABLE_NAME) = 'manga_creative_step'
                  AND LOWER(COLUMN_NAME) IN (
                    'blueprint_snapshot_json', 'blueprint_fingerprint', 'state_session_id'
                  )
                """, Integer.class);
        Integer stateAuditColumnCount = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM INFORMATION_SCHEMA.COLUMNS
                WHERE LOWER(TABLE_NAME) = 'manga_agent_state'
                  AND LOWER(COLUMN_NAME) IN ('created_at', 'updated_at', 'created_by', 'updated_by')
                """, Integer.class);

        assertThat(stateTableCount).isEqualTo(1);
        assertThat(stepSnapshotColumnCount).isEqualTo(3);
        assertThat(stateAuditColumnCount).isEqualTo(4);
    }

    /**
     * 使用真实 Mapper 验证 AgentScope 状态可写入、覆盖递增版本并恢复。
     */
    @Test
    void agentStateStorePersistsAndRestoresVersionedState() {
        String sessionId = "test-state-" + UUID.randomUUID();
        String userKey = "1";
        String stateKey = "director";
        CreativeRun stateRun = runRepository.create(
                CreativeAgentTestSupport.run(1L));
        stateSessions.register(stateRun, sessionId);
        agentStateStore.save(userKey, sessionId, stateKey, AgentState.builder()
                .userId(userKey)
                .sessionId(sessionId)
                .summary("first")
                .build());
        agentStateStore.save(userKey, sessionId, stateKey, AgentState.builder()
                .userId(userKey)
                .sessionId(sessionId)
                .summary("second")
                .build());

        VersionedState<AgentState> restored = agentStateStore.getVersioned(
                userKey, sessionId, stateKey, AgentState.class);

        assertThat(restored.version()).isEqualTo(2L);
        assertThat(restored.value().getSummary()).isEqualTo("second");
    }

    /**
     * 使用真实 Mapper、线程池和事务验证 Run 从受理到完成的整条链路。
     */
    @Test
    void creativeRuntimeExecutesRunToCompletion() throws InterruptedException {
        Long userId = jdbcTemplate.queryForObject(
                "SELECT id FROM manga_user ORDER BY id ASC LIMIT 1", Long.class);
        String requestId = UUID.randomUUID().toString();

        CreativeRunStartResult started = creativeRuntime.start(StartCreativeRunCommand.conversation(
                userId, requestId, null, null, "验证创作运行受理链路"));

        assertThat(started.status()).isEqualTo(CreativeRunStatus.QUEUED);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM manga_creative_step WHERE run_id = ?", Integer.class, started.runId()))
                .isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM manga_creative_dispatch_outbox WHERE step_id = "
                        + "(SELECT step_id FROM manga_creative_step WHERE run_id = ?)",
                Integer.class, started.runId()))
                .isEqualTo(1);

        awaitRunStatus(started.runId(), CreativeRunStatus.COMPLETED);

        assertThat(jdbcTemplate.queryForObject(
                "SELECT status FROM manga_creative_run WHERE run_id = ?", String.class, started.runId()))
                .isEqualTo(CreativeRunStatus.COMPLETED.code());
        assertThat(jdbcTemplate.queryForObject(
                "SELECT status FROM manga_creative_step WHERE run_id = ?", String.class, started.runId()))
                .isEqualTo("COMPLETED");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM manga_creative_event WHERE run_id = ?", Integer.class, started.runId()))
                .isGreaterThanOrEqualTo(7);
    }

    /**
     * 验证取消会传播到根 Step 并最终产生 CANCELLED 聚合终态。
     */
    @Test
    void creativeRuntimeCancelsActiveRun() throws InterruptedException {
        Long userId = jdbcTemplate.queryForObject(
                "SELECT id FROM manga_user ORDER BY id ASC LIMIT 1", Long.class);
        CreativeRunStartResult started = creativeRuntime.start(StartCreativeRunCommand.conversation(
                userId, UUID.randomUUID().toString(), null, null, "验证创作运行取消链路"));

        creativeRuntime.cancel(new CancelCreativeRunCommand(started.runId(), userId));
        awaitRunStatus(started.runId(), CreativeRunStatus.CANCELLED);

        assertThat(jdbcTemplate.queryForObject(
                "SELECT status FROM manga_creative_run WHERE run_id = ?", String.class, started.runId()))
                .isEqualTo(CreativeRunStatus.CANCELLED.code());
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM manga_creative_event WHERE run_id = ? AND event_type = 'RUN_CANCELLED'",
                Integer.class, started.runId())).isEqualTo(1);
    }

    /** 在限定时间内等待异步 Run 到达期望状态。 */
    private void awaitRunStatus(String runId, CreativeRunStatus expected) throws InterruptedException {
        awaitRunStatus(runId, expected, 5000L);
    }

    /** 异步媒体需要允许作业扫描重新领取。 */
    private void awaitRunStatus(String runId, CreativeRunStatus expected, long timeoutMs) throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            String status = jdbcTemplate.queryForObject(
                    "SELECT status FROM manga_creative_run WHERE run_id = ?", String.class, runId);
            if (expected.code().equals(status)) {
                return;
            }
            Thread.sleep(20L);
        }
        assertThat(jdbcTemplate.queryForObject(
                "SELECT status FROM manga_creative_run WHERE run_id = ?", String.class, runId))
                .isEqualTo(expected.code());
    }

    /**
     * 确认 OpenAPI 文档只暴露统一 Creative Run 操作。
     */
    @Test
    void openApiDocumentationContainsCreativeRunOperations() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/v1/creative-runs'].post.summary")
                        .value("创建 Creative Run"))
                .andExpect(jsonPath("$.paths['/api/v1/creative-runs/{runId}/events'].get.summary")
                        .value("订阅 Creative Run 事件"))
                .andExpect(jsonPath("$.paths['/api/v1/agent/runs']").doesNotExist());
    }
}
