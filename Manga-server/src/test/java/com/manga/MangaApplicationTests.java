package com.manga;

import com.manga.agent.CreativeAgentTestSupport;
import com.manga.agent.application.CancelCreativeRunCommand;
import com.manga.agent.application.CreativeRunStartResult;
import com.manga.agent.application.CreativeRuntime;
import com.manga.agent.application.StartCreativeRunCommand;
import com.manga.agent.blueprint.AgentBlueprintCompiler;
import com.manga.agent.domain.CreativeRunStatus;
import com.manga.agent.harness.MangaAgentInvoker;
import com.manga.agent.state.AgentStateSessionService;
import com.manga.entity.CreativeRun;
import com.manga.repository.CreativeRunRepository;
import io.agentscope.core.event.AgentEvent;
import io.agentscope.core.event.TextBlockDeltaEvent;
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
import org.springframework.test.web.servlet.MockMvc;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 验证应用上下文能够正常创建。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MangaApplicationTests {
    @MockitoBean
    private AgentBlueprintCompiler blueprintCompiler;
    @MockitoBean
    private MangaAgentInvoker invoker;
    @Autowired
    private CreativeRunRepository runRepository;
    @Autowired
    private AgentStateSessionService stateSessions;

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

        assertThat(actualTables).containsExactlyInAnyOrderElementsOf(expectedTables);
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
        long deadline = System.currentTimeMillis() + 5000L;
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
