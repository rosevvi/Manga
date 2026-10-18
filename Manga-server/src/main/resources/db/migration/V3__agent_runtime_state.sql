ALTER TABLE manga_creative_step
    ADD COLUMN blueprint_snapshot_json LONGTEXT NULL COMMENT 'Agent Step 执行前固化的不含密钥 Blueprint 快照';

ALTER TABLE manga_creative_step
    ADD COLUMN blueprint_fingerprint CHAR(64) NULL COMMENT 'Agent Blueprint 快照的 SHA-256 指纹';

ALTER TABLE manga_creative_step
    ADD COLUMN state_session_id VARCHAR(128) NULL COMMENT 'AgentScope 状态恢复使用的会话标识';

ALTER TABLE manga_creative_step
    ADD CONSTRAINT ck_manga_creative_step_blueprint_pair CHECK (
        (blueprint_snapshot_json IS NULL AND blueprint_fingerprint IS NULL AND state_session_id IS NULL)
        OR
        (blueprint_snapshot_json IS NOT NULL AND blueprint_fingerprint IS NOT NULL AND state_session_id IS NOT NULL)
    );

CREATE INDEX idx_manga_creative_step_state_session
    ON manga_creative_step (state_session_id, status, id);

CREATE TABLE manga_agent_state (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT 'Agent 状态数据库主键',
    user_key VARCHAR(128) NOT NULL COMMENT 'AgentScope 用户隔离键',
    state_session_id VARCHAR(128) NOT NULL COMMENT 'AgentScope 状态会话标识',
    state_key VARCHAR(160) NOT NULL COMMENT 'AgentScope 会话内组件状态键',
    state_kind VARCHAR(16) NOT NULL COMMENT '状态载荷类型：SINGLE 或 LIST',
    state_type VARCHAR(255) NOT NULL COMMENT '序列化前的 State Java 类型名称',
    state_json LONGTEXT NOT NULL COMMENT 'AgentScope State JSON 载荷',
    state_version BIGINT NOT NULL DEFAULT 1 COMMENT '乐观并发控制版本号',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '记录创建时间',
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '记录最后更新时间',
    created_by VARCHAR(128) NOT NULL DEFAULT 'system:database-migration' COMMENT '记录创建操作者',
    updated_by VARCHAR(128) NOT NULL DEFAULT 'system:database-migration' COMMENT '记录最后更新操作者',
    PRIMARY KEY (id),
    CONSTRAINT uk_manga_agent_state_component UNIQUE (user_key, state_session_id, state_key),
    CONSTRAINT ck_manga_agent_state_kind CHECK (state_kind IN ('SINGLE', 'LIST')),
    CONSTRAINT ck_manga_agent_state_version CHECK (state_version > 0),
    INDEX idx_manga_agent_state_user_session (user_key, state_session_id, id)
) COMMENT = 'AgentScope 可恢复组件状态表';
