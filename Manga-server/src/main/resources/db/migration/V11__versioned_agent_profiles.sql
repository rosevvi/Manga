CREATE TABLE manga_agent_profile (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT 'Agent 配置聚合主键',
    profile_key VARCHAR(64) NOT NULL COMMENT '数据库稳定标识，与内置执行角色枚举独立',
    built_in BOOLEAN NOT NULL DEFAULT FALSE COMMENT '是否由平台初始化的内置定义',
    enabled BOOLEAN NOT NULL DEFAULT TRUE COMMENT '是否允许新的运行选择',
    active_version BIGINT NULL COMMENT '当前生效的已发布版本，未发布为空',
    last_version BIGINT NOT NULL DEFAULT 0 COMMENT '已分配的最大版本号',
    revision BIGINT NOT NULL DEFAULT 0 COMMENT '管理员操作的乐观并发版本',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    created_by VARCHAR(128) NOT NULL COMMENT '创建操作者',
    updated_by VARCHAR(128) NOT NULL COMMENT '更新操作者',
    PRIMARY KEY (id),
    CONSTRAINT uk_agent_profile_key UNIQUE (profile_key)
) COMMENT = '管理员 Agent 配置身份、启停和发布指针';

CREATE TABLE manga_agent_profile_version (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '版本主键',
    profile_key VARCHAR(64) NOT NULL COMMENT '所属配置稳定标识',
    version_no BIGINT NOT NULL COMMENT '配置内递增版本号',
    status VARCHAR(32) NOT NULL COMMENT 'DRAFT 可修改，PUBLISHED 不可修改',
    definition_json LONGTEXT NOT NULL COMMENT 'Prompt、能力白名单、模型策略和预算的严格 JSON，不含密钥',
    definition_fingerprint CHAR(64) NOT NULL COMMENT '规范化定义 SHA256',
    published_at TIMESTAMP NULL COMMENT '首次发布的时间',
    published_by VARCHAR(128) NULL COMMENT '首次发布的操作者',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    created_by VARCHAR(128) NOT NULL COMMENT '创建操作者',
    updated_by VARCHAR(128) NOT NULL COMMENT '更新操作者',
    PRIMARY KEY (id),
    CONSTRAINT uk_agent_profile_version UNIQUE (profile_key, version_no),
    CONSTRAINT ck_agent_profile_version_status CHECK (status IN ('DRAFT', 'PUBLISHED')),
    CONSTRAINT fk_agent_version_profile FOREIGN KEY (profile_key) REFERENCES manga_agent_profile (profile_key)
) COMMENT = 'Agent 定义版本，发布后内容不可变';

CREATE TABLE manga_agent_profile_audit (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '审计主键及分页游标',
    profile_key VARCHAR(64) NOT NULL COMMENT '所属 Agent 标识',
    version_no BIGINT NULL COMMENT '操作涉及的版本',
    action VARCHAR(32) NOT NULL COMMENT '初始化、创建、编辑、发布、回滚、启停操作编码',
    previous_active_version BIGINT NULL COMMENT '操作前发布指针',
    active_version BIGINT NULL COMMENT '操作后发布指针',
    profile_revision BIGINT NOT NULL COMMENT '操作后的聚合版本',
    definition_fingerprint CHAR(64) NULL COMMENT '相关定义指纹，不复制 Prompt 正文',
    comment VARCHAR(1000) NULL COMMENT '管理员说明的操作原因',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '操作时间',
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '审计记录不再修改',
    created_by VARCHAR(128) NOT NULL COMMENT '操作人或明确的系统操作者',
    updated_by VARCHAR(128) NOT NULL COMMENT '与创建操作者一致',
    PRIMARY KEY (id),
    CONSTRAINT fk_agent_audit_profile FOREIGN KEY (profile_key) REFERENCES manga_agent_profile (profile_key),
    INDEX idx_agent_audit_cursor (profile_key, id)
) COMMENT = 'Agent 配置生命周期的追加式审计日志';
