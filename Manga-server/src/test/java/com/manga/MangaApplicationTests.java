package com.manga;

import com.manga.agent.application.CancelCreativeRunCommand;
import com.manga.agent.application.CreativeRunStartResult;
import com.manga.agent.application.CreativeRuntime;
import com.manga.agent.application.StartCreativeRunCommand;
import com.manga.agent.domain.CreativeRunStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
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

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CreativeRuntime creativeRuntime;

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
        assertThat(migrationCount).isGreaterThanOrEqualTo(2);
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
                "manga_creative_run",
                "manga_creative_event",
                "manga_creative_event_outbox"
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
     * 使用真实 Mapper 和事务验证 Run 受理、事件写入及取消链路。
     */
    @Test
    void creativeRuntimePersistsAndCancelsRun() {
        Long userId = jdbcTemplate.queryForObject(
                "SELECT id FROM manga_user ORDER BY id ASC LIMIT 1", Long.class);
        String requestId = UUID.randomUUID().toString();

        CreativeRunStartResult started = creativeRuntime.start(StartCreativeRunCommand.conversation(
                userId, requestId, null, null, "验证创作运行受理链路"));

        assertThat(started.status()).isEqualTo(CreativeRunStatus.QUEUED);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM manga_creative_event WHERE run_id = ?", Integer.class, started.runId()))
                .isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM manga_creative_event_outbox WHERE run_id = ?", Integer.class, started.runId()))
                .isEqualTo(1);

        creativeRuntime.cancel(new CancelCreativeRunCommand(started.runId(), userId));

        assertThat(jdbcTemplate.queryForObject(
                "SELECT status FROM manga_creative_run WHERE run_id = ?", String.class, started.runId()))
                .isEqualTo(CreativeRunStatus.CANCELLED.code());
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM manga_creative_event WHERE run_id = ?", Integer.class, started.runId()))
                .isEqualTo(3);
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
