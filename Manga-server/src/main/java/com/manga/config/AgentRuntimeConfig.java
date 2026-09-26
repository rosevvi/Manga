package com.manga.config;

import com.manga.agent.domain.RuntimeInstanceIdentity;
import com.manga.config.properties.ApplicationProperties;
import com.manga.config.properties.MangaAgentProperties;
import io.agentscope.core.state.AgentStateStore;
import io.agentscope.core.state.InMemoryAgentStateStore;
import io.agentscope.extensions.mysql.state.MysqlAgentStateStore;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.util.StringUtils;

import javax.sql.DataSource;
import java.util.UUID;

/** 配置由平台管理的 AgentScope 状态存储和后台投递任务。 */
@Configuration
@EnableScheduling
@EnableConfigurationProperties(MangaAgentProperties.class)
public class AgentRuntimeConfig {

    /** 创建 AgentScope 状态存储；测试环境可以显式切换为内存实现。 */
    @Bean
    public AgentStateStore mangaAgentStateStore(DataSource dataSource, MangaAgentProperties properties) {
        if (properties.getState().getMode() == MangaAgentProperties.Mode.IN_MEMORY) {
            return new InMemoryAgentStateStore();
        }
        return new MysqlAgentStateStore(dataSource, properties.getState().getDatabaseName(),
                properties.getState().getTableName(), false);
    }

    /**
     * 创建当前服务进程的租约身份；生产环境可固定注入实例 ID，本地缺省时自动生成。
     */
    @Bean
    public RuntimeInstanceIdentity runtimeInstanceIdentity(
            ApplicationProperties applicationProperties,
            MangaAgentProperties properties) {
        String configuredInstanceId = properties.getRuntime().getInstanceId();
        if (StringUtils.hasText(configuredInstanceId)) {
            return new RuntimeInstanceIdentity(configuredInstanceId.trim());
        }
        return new RuntimeInstanceIdentity(applicationProperties.name() + '-' + UUID.randomUUID());
    }
}
