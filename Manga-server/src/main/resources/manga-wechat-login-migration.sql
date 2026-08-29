-- 已存在 Manga 用户表的数据库执行一次本迁移；新数据库直接执行 manga.sql。
ALTER TABLE manga_user
    MODIFY COLUMN password_hash VARCHAR(100) NULL COMMENT '账号密码摘要，第三方登录账号可为空',
    ADD COLUMN registration_source VARCHAR(32) NOT NULL DEFAULT 'PASSWORD' COMMENT '账号初始注册来源' AFTER status;

CREATE TABLE manga_user_identity (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '外部身份主键',
    user_id BIGINT NOT NULL COMMENT '平台用户主键',
    provider VARCHAR(32) NOT NULL COMMENT '外部身份提供方',
    provider_user_id VARCHAR(128) NOT NULL COMMENT '提供方内用户唯一标识',
    provider_union_id VARCHAR(128) NULL COMMENT '提供方跨应用统一用户标识',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后修改时间',
    created_by VARCHAR(64) NOT NULL COMMENT '创建人标识',
    updated_by VARCHAR(64) NOT NULL COMMENT '最后修改人标识',
    PRIMARY KEY (id),
    CONSTRAINT uk_manga_user_identity_provider_user UNIQUE (provider, provider_user_id),
    CONSTRAINT fk_manga_user_identity_user FOREIGN KEY (user_id) REFERENCES manga_user (id) ON DELETE CASCADE
) COMMENT = '用户外部登录身份表';

CREATE TABLE manga_external_login_session (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '外部登录会话主键',
    login_token CHAR(36) NOT NULL COMMENT '浏览器轮询使用的一次性登录令牌',
    provider VARCHAR(32) NOT NULL COMMENT '外部身份提供方',
    provider_scene_key VARCHAR(64) NOT NULL COMMENT '提供方二维码场景标识',
    provider_ticket VARCHAR(512) NOT NULL COMMENT '提供方返回的二维码票据',
    provider_qr_url VARCHAR(1024) NOT NULL COMMENT '二维码展示地址',
    status VARCHAR(20) NOT NULL COMMENT '外部登录会话状态',
    user_id BIGINT NULL COMMENT '扫码确认后的平台用户主键',
    expires_at TIMESTAMP NOT NULL COMMENT '登录会话过期时间',
    consumed_at TIMESTAMP NULL COMMENT '登录令牌兑换时间',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后修改时间',
    created_by VARCHAR(64) NOT NULL COMMENT '创建人标识',
    updated_by VARCHAR(64) NOT NULL COMMENT '最后修改人标识',
    PRIMARY KEY (id),
    CONSTRAINT uk_manga_external_login_token UNIQUE (login_token),
    CONSTRAINT uk_manga_external_login_scene UNIQUE (provider, provider_scene_key),
    CONSTRAINT fk_manga_external_login_user FOREIGN KEY (user_id) REFERENCES manga_user (id) ON DELETE SET NULL
) COMMENT = '外部扫码登录会话表';
