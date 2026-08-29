-- 已按旧版 manga.sql 建表的数据库仅执行一次本迁移；新数据库直接执行 manga.sql。
SET @system_actor = 'SYSTEM';

ALTER TABLE manga_user
    MODIFY COLUMN id BIGINT NOT NULL AUTO_INCREMENT COMMENT '用户主键',
    MODIFY COLUMN username VARCHAR(64) NOT NULL COMMENT '登录用户名',
    MODIFY COLUMN password_hash VARCHAR(100) NOT NULL COMMENT 'BCrypt 密码摘要',
    MODIFY COLUMN display_name VARCHAR(64) NOT NULL COMMENT '用户显示名称',
    MODIFY COLUMN status VARCHAR(20) NOT NULL COMMENT '账号状态',
    MODIFY COLUMN created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    MODIFY COLUMN updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后修改时间',
    ADD COLUMN created_by VARCHAR(64) NULL COMMENT '创建人标识' AFTER updated_at,
    ADD COLUMN updated_by VARCHAR(64) NULL COMMENT '最后修改人标识' AFTER created_by,
    COMMENT = '平台用户表';

UPDATE manga_user
SET created_by = COALESCE(NULLIF(created_by, ''), @system_actor),
    updated_by = COALESCE(NULLIF(updated_by, ''), @system_actor);

ALTER TABLE manga_user
    MODIFY COLUMN created_by VARCHAR(64) NOT NULL COMMENT '创建人标识',
    MODIFY COLUMN updated_by VARCHAR(64) NOT NULL COMMENT '最后修改人标识';

ALTER TABLE manga_role
    MODIFY COLUMN id BIGINT NOT NULL AUTO_INCREMENT COMMENT '角色主键',
    MODIFY COLUMN code VARCHAR(32) NOT NULL COMMENT '角色编码',
    MODIFY COLUMN name VARCHAR(64) NOT NULL COMMENT '角色名称',
    MODIFY COLUMN description VARCHAR(255) NOT NULL COMMENT '角色用途说明',
    MODIFY COLUMN created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    MODIFY COLUMN updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后修改时间',
    ADD COLUMN created_by VARCHAR(64) NULL COMMENT '创建人标识' AFTER updated_at,
    ADD COLUMN updated_by VARCHAR(64) NULL COMMENT '最后修改人标识' AFTER created_by,
    COMMENT = '平台角色表';

UPDATE manga_role
SET created_by = COALESCE(NULLIF(created_by, ''), @system_actor),
    updated_by = COALESCE(NULLIF(updated_by, ''), @system_actor);

ALTER TABLE manga_role
    MODIFY COLUMN created_by VARCHAR(64) NOT NULL COMMENT '创建人标识',
    MODIFY COLUMN updated_by VARCHAR(64) NOT NULL COMMENT '最后修改人标识';

ALTER TABLE manga_user_role
    MODIFY COLUMN user_id BIGINT NOT NULL COMMENT '用户主键',
    MODIFY COLUMN role_id BIGINT NOT NULL COMMENT '角色主键',
    MODIFY COLUMN created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    ADD COLUMN updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后修改时间' AFTER created_at,
    ADD COLUMN created_by VARCHAR(64) NULL COMMENT '创建人标识' AFTER updated_at,
    ADD COLUMN updated_by VARCHAR(64) NULL COMMENT '最后修改人标识' AFTER created_by,
    COMMENT = '用户角色关联表';

UPDATE manga_user_role
SET created_by = COALESCE(NULLIF(created_by, ''), @system_actor),
    updated_by = COALESCE(NULLIF(updated_by, ''), @system_actor);

ALTER TABLE manga_user_role
    MODIFY COLUMN created_by VARCHAR(64) NOT NULL COMMENT '创建人标识',
    MODIFY COLUMN updated_by VARCHAR(64) NOT NULL COMMENT '最后修改人标识';
