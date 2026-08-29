-- 为已有 Manga 数据库增加用户 AI 服务配置，可在 DBeaver 中直接执行。
CREATE TABLE IF NOT EXISTS manga_ai_provider_config (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT 'AI 服务配置主键',
    owner_user_id BIGINT NOT NULL COMMENT '配置所有者用户主键',
    name VARCHAR(80) NOT NULL COMMENT '用户自定义配置名称',
    provider_type VARCHAR(32) NOT NULL COMMENT 'AI 服务商或兼容协议类型',
    base_url VARCHAR(1024) NOT NULL COMMENT 'AI 服务 API 根地址',
    default_model VARCHAR(160) NULL COMMENT '未来调用时默认使用的模型编码',
    api_key_ciphertext TEXT NULL COMMENT 'AES-GCM 加密后的 API Key 密文',
    api_key_hint VARCHAR(32) NULL COMMENT '用于界面确认的 API Key 脱敏摘要',
    enabled BOOLEAN NOT NULL DEFAULT TRUE COMMENT '配置是否允许用于 AI 调用',
    default_config BOOLEAN NOT NULL DEFAULT FALSE COMMENT '是否为用户当前默认配置',
    remark VARCHAR(500) NULL COMMENT '配置用途或注意事项',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后修改时间',
    created_by VARCHAR(64) NOT NULL COMMENT '创建人标识',
    updated_by VARCHAR(64) NOT NULL COMMENT '最后修改人标识',
    PRIMARY KEY (id),
    CONSTRAINT uk_manga_ai_config_owner_name UNIQUE (owner_user_id, name),
    INDEX idx_manga_ai_config_owner_default (owner_user_id, default_config, enabled),
    CONSTRAINT fk_manga_ai_config_owner FOREIGN KEY (owner_user_id) REFERENCES manga_user (id) ON DELETE CASCADE
) COMMENT = '用户 AI 服务接入配置表';
