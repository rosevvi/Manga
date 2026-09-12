package com.manga.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/** 绑定助手运行时的持久化和执行限制配置。 */
@ConfigurationProperties(prefix = "manga.agent")
public class MangaAgentProperties {

    private final State state = new State();
    private final Runtime runtime = new Runtime();

    public State getState() {
        return state;
    }

    public Runtime getRuntime() {
        return runtime;
    }

    public static class State {
        private Mode mode = Mode.MYSQL;
        private String databaseName = "Manga";
        private String tableName = "manga_agent_state";

        public Mode getMode() {
            return mode;
        }

        public void setMode(Mode mode) {
            this.mode = mode;
        }

        public String getDatabaseName() {
            return databaseName;
        }

        public void setDatabaseName(String databaseName) {
            this.databaseName = databaseName;
        }

        public String getTableName() {
            return tableName;
        }

        public void setTableName(String tableName) {
            this.tableName = tableName;
        }
    }

    public enum Mode {
        MYSQL,
        IN_MEMORY
    }

    public static class Runtime {
        private Duration ownerLease = Duration.ofSeconds(30);
        private Duration runTimeout = Duration.ofMinutes(10);
        private int maxIterations = 12;

        public Duration getOwnerLease() {
            return ownerLease;
        }

        public void setOwnerLease(Duration ownerLease) {
            this.ownerLease = ownerLease;
        }

        public Duration getRunTimeout() {
            return runTimeout;
        }

        public void setRunTimeout(Duration runTimeout) {
            this.runTimeout = runTimeout;
        }

        public int getMaxIterations() {
            return maxIterations;
        }

        public void setMaxIterations(int maxIterations) {
            this.maxIterations = maxIterations;
        }
    }
}
