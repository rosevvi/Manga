ALTER TABLE manga_storyboard_shot
    ADD COLUMN IF NOT EXISTS chapter_id BIGINT NULL COMMENT '关联剧本章节主键';

CREATE TABLE manga_agent_conversation (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '助手会话主键',
    conversation_id CHAR(36) NOT NULL COMMENT '会话唯一标识',
    user_id BIGINT NOT NULL COMMENT '会话所属用户',
    project_id BIGINT NULL COMMENT '可选绑定的项目',
    title VARCHAR(160) NOT NULL COMMENT '会话标题',
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' COMMENT '会话状态',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    CONSTRAINT uk_manga_agent_conversation_id UNIQUE (conversation_id),
    INDEX idx_manga_agent_conversation_user_updated (user_id, updated_at),
    INDEX idx_manga_agent_conversation_project_updated (project_id, updated_at),
    CONSTRAINT fk_manga_agent_conversation_user FOREIGN KEY (user_id) REFERENCES manga_user (id) ON DELETE CASCADE,
    CONSTRAINT fk_manga_agent_conversation_project FOREIGN KEY (project_id) REFERENCES manga_project (id) ON DELETE SET NULL
) COMMENT = '助手会话表';

CREATE TABLE manga_agent_run (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '助手运行主键',
    run_id CHAR(36) NOT NULL COMMENT '运行唯一标识',
    conversation_id CHAR(36) NOT NULL COMMENT '所属会话标识',
    user_id BIGINT NOT NULL COMMENT '发起用户',
    project_id BIGINT NULL COMMENT '运行绑定项目',
    agent_key VARCHAR(64) NOT NULL COMMENT 'Agent 定义键',
    provider_config_id BIGINT NULL COMMENT '提交时使用的 AI 配置历史标识',
    model_code VARCHAR(160) NOT NULL COMMENT '提交时使用的模型编码',
    kernel_fingerprint CHAR(64) NOT NULL COMMENT '不可变运行配置指纹',
    kernel_snapshot_json LONGTEXT NOT NULL COMMENT '不可变运行配置快照',
    state_session_id VARCHAR(255) NOT NULL COMMENT 'AgentScope 状态会话标识',
    status VARCHAR(32) NOT NULL COMMENT '运行状态',
    active_conversation_id CHAR(36) NULL COMMENT '运行中会话唯一约束键',
    owner_instance_id VARCHAR(128) NULL COMMENT '当前执行实例标识',
    owner_epoch BIGINT NOT NULL DEFAULT 0 COMMENT '执行实例租约代次',
    lease_until TIMESTAMP NULL COMMENT '执行租约截止时间',
    deadline_at TIMESTAMP NOT NULL COMMENT '运行截止时间',
    next_sequence BIGINT NOT NULL DEFAULT 1 COMMENT '下一个事件序号',
    error_code VARCHAR(64) NULL COMMENT '终态错误编码',
    error_message VARCHAR(2000) NULL COMMENT '终态错误摘要',
    started_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '开始时间',
    finished_at TIMESTAMP NULL COMMENT '结束时间',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    CONSTRAINT uk_manga_agent_run_id UNIQUE (run_id),
    CONSTRAINT uk_manga_agent_run_active_conversation UNIQUE (active_conversation_id),
    INDEX idx_manga_agent_run_conversation_status (conversation_id, status, id),
    INDEX idx_manga_agent_run_user_updated (user_id, updated_at),
    INDEX idx_manga_agent_run_lease (status, lease_until),
    CONSTRAINT fk_manga_agent_run_user FOREIGN KEY (user_id) REFERENCES manga_user (id) ON DELETE CASCADE,
    CONSTRAINT fk_manga_agent_run_project FOREIGN KEY (project_id) REFERENCES manga_project (id) ON DELETE SET NULL
) COMMENT = '助手运行表';

CREATE TABLE manga_agent_event (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '助手事件主键',
    run_id CHAR(36) NOT NULL COMMENT '所属运行标识',
    sequence_no BIGINT NOT NULL COMMENT '运行内递增事件序号',
    event_type VARCHAR(64) NOT NULL COMMENT '事件类型',
    payload_json LONGTEXT NOT NULL COMMENT '脱敏后的事件载荷',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (id),
    CONSTRAINT uk_manga_agent_event_sequence UNIQUE (run_id, sequence_no),
    INDEX idx_manga_agent_event_run_sequence (run_id, sequence_no),
    CONSTRAINT fk_manga_agent_event_run FOREIGN KEY (run_id) REFERENCES manga_agent_run (run_id) ON DELETE CASCADE
) COMMENT = '助手运行事件日志';

CREATE TABLE manga_agent_message (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '助手消息主键',
    conversation_id CHAR(36) NOT NULL COMMENT '所属会话标识',
    run_id CHAR(36) NULL COMMENT '关联运行标识',
    role VARCHAR(20) NOT NULL COMMENT '消息角色',
    content LONGTEXT NOT NULL COMMENT '消息正文',
    message_order BIGINT NOT NULL COMMENT '会话内稳定排序',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (id),
    CONSTRAINT uk_manga_agent_message_order UNIQUE (conversation_id, message_order),
    INDEX idx_manga_agent_message_conversation (conversation_id, message_order),
    CONSTRAINT fk_manga_agent_message_conversation FOREIGN KEY (conversation_id) REFERENCES manga_agent_conversation (conversation_id) ON DELETE CASCADE,
    CONSTRAINT fk_manga_agent_message_run FOREIGN KEY (run_id) REFERENCES manga_agent_run (run_id) ON DELETE SET NULL
) COMMENT = '助手消息投影表';

CREATE TABLE manga_agent_state (
    session_id VARCHAR(255) NOT NULL COMMENT 'AgentScope 会话标识',
    state_key VARCHAR(255) NOT NULL COMMENT '状态键',
    item_index INT NOT NULL DEFAULT 0 COMMENT '状态分片序号',
    state_data LONGTEXT NOT NULL COMMENT '状态内容',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (session_id, state_key, item_index),
    INDEX idx_manga_agent_state_updated (updated_at)
) COMMENT = 'AgentScope 持久化状态表';

CREATE TABLE manga_agent_event_outbox (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '助手事件出站主键',
    run_id CHAR(36) NOT NULL COMMENT '所属运行标识',
    sequence_no BIGINT NOT NULL COMMENT '所属事件序号',
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING' COMMENT '投递状态',
    published_at TIMESTAMP NULL COMMENT '投递完成时间',
    attempts INT NOT NULL DEFAULT 0 COMMENT '投递次数',
    last_error VARCHAR(1000) NULL COMMENT '最近投递错误',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    CONSTRAINT uk_manga_agent_outbox_event UNIQUE (run_id, sequence_no),
    INDEX idx_manga_agent_outbox_status (status, id),
    CONSTRAINT fk_manga_agent_outbox_event FOREIGN KEY (run_id, sequence_no) REFERENCES manga_agent_event (run_id, sequence_no) ON DELETE CASCADE
) COMMENT = '助手事件投递箱';
