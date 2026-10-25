ALTER TABLE manga_creative_conversation
    ADD COLUMN state_generation BIGINT NOT NULL DEFAULT 1 COMMENT 'Director 状态隔离代次，失败或取消后递增';
ALTER TABLE manga_creative_conversation
    ADD COLUMN memory_summary LONGTEXT NULL COMMENT '已完成消息的有界提取式上下文摘要';
ALTER TABLE manga_creative_conversation
    ADD COLUMN summarized_through_message_id BIGINT NOT NULL DEFAULT 0 COMMENT '摘要已覆盖的消息主键';
ALTER TABLE manga_creative_run
    ADD COLUMN projected_through_sequence BIGINT NOT NULL DEFAULT 0 COMMENT '消息投影已原子处理的事件序号';
ALTER TABLE manga_creative_run
    ADD COLUMN state_generation BIGINT NOT NULL DEFAULT 1 COMMENT '受理时冻结的会话状态隔离代次';

CREATE TABLE manga_creative_message (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '消息投影主键',
    conversation_id CHAR(36) NOT NULL COMMENT '所属创作会话标识',
    run_id CHAR(36) NOT NULL COMMENT '消息来源运行标识',
    projection_key CHAR(64) NOT NULL COMMENT '由运行和逻辑消息标识计算的幂等投影键',
    role VARCHAR(16) NOT NULL COMMENT '消息角色：USER、ASSISTANT 或 TOOL',
    status VARCHAR(32) NOT NULL COMMENT '消息状态：ACTIVE、COMPLETED、FAILED 或 CANCELLED',
    content LONGTEXT NOT NULL COMMENT '可供用户查看的完整文本',
    tool_call_id VARCHAR(160) NULL COMMENT '工具调用标识，非工具消息为空',
    tool_name VARCHAR(160) NULL COMMENT '稳定工具名称，非工具消息为空',
    tool_status VARCHAR(32) NULL COMMENT '公开工具执行状态',
    first_sequence BIGINT NOT NULL COMMENT '创建此消息的运行内事件序号',
    last_sequence BIGINT NOT NULL COMMENT '此消息已消费的最后事件序号',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '记录创建时间',
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '记录更新时间',
    created_by VARCHAR(128) NOT NULL COMMENT '记录创建操作者',
    updated_by VARCHAR(128) NOT NULL COMMENT '记录更新操作者',
    PRIMARY KEY (id),
    CONSTRAINT uk_manga_message_projection UNIQUE (projection_key),
    CONSTRAINT fk_manga_message_conversation FOREIGN KEY (conversation_id) REFERENCES manga_creative_conversation (conversation_id) ON DELETE CASCADE,
    CONSTRAINT fk_manga_message_run FOREIGN KEY (run_id) REFERENCES manga_creative_run (run_id) ON DELETE CASCADE,
    CONSTRAINT ck_manga_message_role CHECK (role IN ('USER', 'ASSISTANT', 'TOOL')),
    INDEX idx_manga_message_conversation (conversation_id, id),
    INDEX idx_manga_message_run (run_id, first_sequence)
) COMMENT = '由运行事件重建的创作消息投影表';

CREATE TABLE manga_agent_session (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT 'Agent 状态会话主键',
    state_session_id VARCHAR(128) NOT NULL COMMENT '单次 Run 独占的状态会话标识',
    run_id CHAR(36) NOT NULL COMMENT '所属运行标识',
    user_key VARCHAR(128) NOT NULL COMMENT '状态写入的用户隔离键',
    generation BIGINT NOT NULL COMMENT '受理时冻结的会话状态代次',
    status VARCHAR(16) NOT NULL COMMENT '状态写入许可：ACTIVE 或 RETIRED',
    expires_at TIMESTAMP NOT NULL COMMENT '本次会话允许写入状态的最晚时间',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '记录创建时间',
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '记录更新时间',
    created_by VARCHAR(128) NOT NULL COMMENT '记录创建操作者',
    updated_by VARCHAR(128) NOT NULL COMMENT '记录更新操作者',
    PRIMARY KEY (id),
    CONSTRAINT uk_manga_agent_session UNIQUE (state_session_id),
    CONSTRAINT fk_manga_agent_session_run FOREIGN KEY (run_id) REFERENCES manga_creative_run (run_id) ON DELETE CASCADE,
    CONSTRAINT ck_manga_agent_session_status CHECK (status IN ('ACTIVE', 'RETIRED')),
    INDEX idx_manga_agent_session_cleanup (expires_at, id),
    INDEX idx_manga_agent_session_run (run_id, status)
) COMMENT = 'Agent 状态写入围栏与清理生命周期表';
