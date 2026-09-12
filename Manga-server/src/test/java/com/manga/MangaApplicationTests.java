package com.manga;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 验证应用上下文能够正常创建。
 */
@SpringBootTest
@ActiveProfiles("test")
class MangaApplicationTests {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    /**
     * 确认基础 Bean 配置不存在启动错误。
     */
    @Test
    void contextLoads() {
    }

    @Test
    void flywayCreatesAgentRuntimeTables() {
        Integer tableCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES WHERE LOWER(TABLE_NAME) = 'manga_agent_run'", Integer.class);
        Integer migrationCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM flyway_schema_history WHERE success = TRUE", Integer.class);

        assertThat(tableCount).isEqualTo(1);
        assertThat(migrationCount).isGreaterThanOrEqualTo(2);
    }
}
