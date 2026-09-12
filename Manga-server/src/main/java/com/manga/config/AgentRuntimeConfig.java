package com.manga.config;

import com.manga.config.properties.MangaAgentProperties;
import io.agentscope.core.state.AgentStateStore;
import io.agentscope.core.state.InMemoryAgentStateStore;
import io.agentscope.extensions.mysql.state.MysqlAgentStateStore;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

import javax.sql.DataSource;

/** 配置由平台管理的 AgentScope 状态存储和后台投递任务。 */
@Configuration
@EnableScheduling
@EnableConfigurationProperties(MangaAgentProperties.class)
public class AgentRuntimeConfig {

    @Bean
    public AgentStateStore mangaAgentStateStore(DataSource dataSource, MangaAgentProperties properties) {
        if (properties.getState().getMode() == MangaAgentProperties.Mode.IN_MEMORY) {
            return new InMemoryAgentStateStore();
        }
        return new MysqlAgentStateStore(dataSource, properties.getState().getDatabaseName(),
                properties.getState().getTableName(), false);
    }
}
