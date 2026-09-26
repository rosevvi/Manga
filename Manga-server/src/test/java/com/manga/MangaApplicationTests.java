package com.manga;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

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
        String conversationNullable = jdbcTemplate.queryForObject("""
                SELECT IS_NULLABLE
                FROM INFORMATION_SCHEMA.COLUMNS
                WHERE LOWER(TABLE_NAME) = 'manga_creative_run'
                  AND LOWER(COLUMN_NAME) = 'conversation_id'
                """, String.class);

        assertThat(tableCount).isEqualTo(1);
        assertThat(migrationCount).isGreaterThanOrEqualTo(2);
        assertThat(triggerColumnCount).isEqualTo(4);
        assertThat(conversationNullable).isEqualTo("YES");
    }

    /**
     * 确认 OpenAPI 文档端点可访问，并包含创作助手接口的操作说明。
     */
    @Test
    void openApiDocumentationContainsAgentOperations() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/v1/agent/runs'].post.summary")
                        .value("启动创作助手 Run"));
    }
}
