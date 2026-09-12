-- Manga 用户与角色基础表。该脚本供新数据库初始化使用，可在 DBeaver 中重复运行。
CREATE TABLE IF NOT EXISTS manga_user (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '用户主键',
    username VARCHAR(64) NOT NULL COMMENT '登录用户名',
    password_hash VARCHAR(100) NULL COMMENT '账号密码摘要，第三方登录账号可为空',
    display_name VARCHAR(64) NOT NULL COMMENT '用户显示名称',
    status VARCHAR(20) NOT NULL COMMENT '账号状态',
    registration_source VARCHAR(32) NOT NULL DEFAULT 'PASSWORD' COMMENT '账号初始注册来源',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后修改时间',
    created_by VARCHAR(64) NOT NULL COMMENT '创建人标识',
    updated_by VARCHAR(64) NOT NULL COMMENT '最后修改人标识',
    PRIMARY KEY (id),
    CONSTRAINT uk_manga_user_username UNIQUE (username)
) COMMENT = '平台用户表';

CREATE TABLE IF NOT EXISTS manga_role (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '角色主键',
    code VARCHAR(32) NOT NULL COMMENT '角色编码',
    name VARCHAR(64) NOT NULL COMMENT '角色名称',
    description VARCHAR(255) NOT NULL COMMENT '角色用途说明',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后修改时间',
    created_by VARCHAR(64) NOT NULL COMMENT '创建人标识',
    updated_by VARCHAR(64) NOT NULL COMMENT '最后修改人标识',
    PRIMARY KEY (id),
    CONSTRAINT uk_manga_role_code UNIQUE (code)
) COMMENT = '平台角色表';

CREATE TABLE IF NOT EXISTS manga_user_role (
    user_id BIGINT NOT NULL COMMENT '用户主键',
    role_id BIGINT NOT NULL COMMENT '角色主键',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后修改时间',
    created_by VARCHAR(64) NOT NULL COMMENT '创建人标识',
    updated_by VARCHAR(64) NOT NULL COMMENT '最后修改人标识',
    PRIMARY KEY (user_id, role_id),
    CONSTRAINT fk_manga_user_role_user FOREIGN KEY (user_id) REFERENCES manga_user (id) ON DELETE CASCADE,
    CONSTRAINT fk_manga_user_role_role FOREIGN KEY (role_id) REFERENCES manga_role (id) ON DELETE CASCADE
) COMMENT = '用户角色关联表';

CREATE TABLE IF NOT EXISTS manga_user_identity (
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

CREATE TABLE IF NOT EXISTS manga_external_login_session (
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

CREATE TABLE IF NOT EXISTS manga_project (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '项目主键',
    owner_user_id BIGINT NOT NULL COMMENT '项目所有者用户主键',
    name VARCHAR(120) NOT NULL COMMENT '项目名称',
    description VARCHAR(1000) NULL COMMENT '项目简介',
    cover_url VARCHAR(1024) NULL COMMENT '项目封面地址',
    genre VARCHAR(64) NULL COMMENT '作品类型',
    aspect_ratio VARCHAR(20) NOT NULL DEFAULT '16:9' COMMENT '项目默认画面比例',
    visibility_scope VARCHAR(20) NOT NULL DEFAULT 'PRIVATE' COMMENT '项目可见范围',
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT' COMMENT '项目状态',
    art_style VARCHAR(64) NULL COMMENT '画风预设标识或 custom',
    art_style_description VARCHAR(2000) NULL COMMENT '自定义画风中文描述',
    art_style_image_prompt VARCHAR(2000) NULL COMMENT '自定义画风图片生成提示词',
    art_style_image_url VARCHAR(1024) NULL COMMENT '画风参考图片地址',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后修改时间',
    created_by VARCHAR(64) NOT NULL COMMENT '创建人标识',
    updated_by VARCHAR(64) NOT NULL COMMENT '最后修改人标识',
    PRIMARY KEY (id),
    INDEX idx_manga_project_owner_updated (owner_user_id, updated_at),
    CONSTRAINT fk_manga_project_owner FOREIGN KEY (owner_user_id) REFERENCES manga_user (id) ON DELETE CASCADE
) COMMENT = '漫剧创作项目表';

CREATE TABLE IF NOT EXISTS manga_project_member (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '项目成员主键',
    project_id BIGINT NOT NULL COMMENT '所属项目主键',
    user_id BIGINT NOT NULL COMMENT '成员用户主键',
    role VARCHAR(20) NOT NULL DEFAULT 'MEMBER' COMMENT '项目成员角色',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后修改时间',
    created_by VARCHAR(64) NOT NULL COMMENT '创建人标识',
    updated_by VARCHAR(64) NOT NULL COMMENT '最后修改人标识',
    PRIMARY KEY (id),
    CONSTRAINT uk_manga_project_member_user UNIQUE (project_id, user_id),
    INDEX idx_manga_project_member_user (user_id, project_id),
    CONSTRAINT fk_manga_project_member_project FOREIGN KEY (project_id) REFERENCES manga_project (id) ON DELETE CASCADE,
    CONSTRAINT fk_manga_project_member_user FOREIGN KEY (user_id) REFERENCES manga_user (id) ON DELETE CASCADE
) COMMENT = '项目协作成员表';

CREATE TABLE IF NOT EXISTS manga_project_workflow (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '项目工作流记录主键',
    project_id BIGINT NOT NULL COMMENT '所属项目主键',
    current_stage VARCHAR(20) NOT NULL DEFAULT 'SCRIPT' COMMENT '当前推荐创作阶段',
    stage_revision INT NOT NULL DEFAULT 0 COMMENT '阶段记录版本号',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后修改时间',
    created_by VARCHAR(64) NOT NULL COMMENT '创建人标识',
    updated_by VARCHAR(64) NOT NULL COMMENT '最后修改人标识',
    PRIMARY KEY (id),
    CONSTRAINT uk_manga_project_workflow_project UNIQUE (project_id),
    CONSTRAINT fk_manga_project_workflow_project FOREIGN KEY (project_id) REFERENCES manga_project (id) ON DELETE CASCADE
) COMMENT = '项目创作工作流阶段表';

CREATE TABLE IF NOT EXISTS manga_project_script (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '项目剧本主键',
    project_id BIGINT NOT NULL COMMENT '所属项目主键',
    title VARCHAR(120) NOT NULL COMMENT '剧本标题',
    synopsis VARCHAR(2000) NULL COMMENT '剧本简介',
    source_type VARCHAR(32) NOT NULL DEFAULT 'MANUAL' COMMENT '原始文本来源类型',
    parse_status VARCHAR(20) NOT NULL DEFAULT 'DRAFT' COMMENT '结构化解析状态',
    structure_version INT NOT NULL DEFAULT 1 COMMENT '结构化数据版本',
    chapter_count INT NOT NULL DEFAULT 0 COMMENT '章节数量',
    last_error VARCHAR(2000) NULL COMMENT '最近一次解析错误摘要',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后修改时间',
    created_by VARCHAR(64) NOT NULL COMMENT '创建人标识',
    updated_by VARCHAR(64) NOT NULL COMMENT '最后修改人标识',
    PRIMARY KEY (id),
    CONSTRAINT uk_manga_project_script_project UNIQUE (project_id),
    CONSTRAINT fk_manga_project_script_project FOREIGN KEY (project_id) REFERENCES manga_project (id) ON DELETE CASCADE
) COMMENT = '项目剧本主表';

CREATE TABLE IF NOT EXISTS manga_project_script_chapter (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '剧本章节主键',
    script_id BIGINT NOT NULL COMMENT '所属剧本主键',
    title VARCHAR(120) NOT NULL COMMENT '章节标题',
    synopsis VARCHAR(2000) NULL COMMENT '章节简介',
    raw_content LONGTEXT NOT NULL COMMENT '当前章节原文',
    structure_json LONGTEXT NULL COMMENT '当前章节结构 JSON',
    source_type VARCHAR(32) NOT NULL DEFAULT 'MANUAL' COMMENT '章节来源类型',
    parse_status VARCHAR(20) NOT NULL DEFAULT 'PENDING' COMMENT '章节处理状态',
    structure_version INT NOT NULL DEFAULT 1 COMMENT '章节结构版本',
    last_error VARCHAR(2000) NULL COMMENT '章节错误摘要',
    sort_order INT NOT NULL COMMENT '稳定排序值',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后修改时间',
    created_by VARCHAR(64) NOT NULL COMMENT '创建人标识',
    updated_by VARCHAR(64) NOT NULL COMMENT '最后修改人标识',
    PRIMARY KEY (id),
    CONSTRAINT uk_manga_script_chapter_order UNIQUE (script_id, sort_order),
    INDEX idx_manga_script_chapter_script (script_id, sort_order),
    CONSTRAINT fk_manga_script_chapter_script FOREIGN KEY (script_id) REFERENCES manga_project_script (id) ON DELETE CASCADE
) COMMENT = '项目剧本章节表';

CREATE TABLE IF NOT EXISTS manga_project_script_scene (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '剧本场景主键',
    chapter_id BIGINT NOT NULL COMMENT '所属章节主键',
    scene_number INT NOT NULL COMMENT '场景编号',
    location VARCHAR(120) NULL COMMENT '场景地点',
    time_description VARCHAR(120) NULL COMMENT '场景时间描述',
    summary VARCHAR(2000) NULL COMMENT '场景摘要',
    content VARCHAR(5000) NULL COMMENT '场景画面内容',
    sort_order INT NOT NULL COMMENT '场景排序',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后修改时间',
    PRIMARY KEY (id),
    CONSTRAINT uk_manga_script_scene_number UNIQUE (chapter_id, scene_number),
    INDEX idx_manga_script_scene_chapter_order (chapter_id, sort_order),
    CONSTRAINT fk_manga_script_scene_chapter FOREIGN KEY (chapter_id) REFERENCES manga_project_script_chapter (id) ON DELETE CASCADE
) COMMENT = '项目剧本场景表';

CREATE TABLE IF NOT EXISTS manga_project_script_dialogue (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '剧本对白主键',
    scene_id BIGINT NOT NULL COMMENT '所属场景主键',
    speaker VARCHAR(120) NULL COMMENT '说话角色',
    text VARCHAR(5000) NOT NULL COMMENT '对白或旁白文本',
    dialogue_type VARCHAR(20) NOT NULL DEFAULT 'DIALOGUE' COMMENT '对白类型',
    sort_order INT NOT NULL COMMENT '对白排序',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后修改时间',
    PRIMARY KEY (id),
    INDEX idx_manga_script_dialogue_scene_order (scene_id, sort_order),
    CONSTRAINT fk_manga_script_dialogue_scene FOREIGN KEY (scene_id) REFERENCES manga_project_script_scene (id) ON DELETE CASCADE
) COMMENT = '项目剧本对白表';

CREATE TABLE IF NOT EXISTS manga_task (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '任务主键',
    owner_user_id BIGINT NOT NULL COMMENT '任务所有者',
    project_id BIGINT NOT NULL COMMENT '所属项目',
    task_type VARCHAR(40) NOT NULL COMMENT '任务类型',
    status VARCHAR(24) NOT NULL COMMENT '任务状态',
    title VARCHAR(160) NOT NULL COMMENT '任务标题',
    source_reference VARCHAR(1024) NULL COMMENT '临时输入文件引用',
    source_sha256 CHAR(64) NULL COMMENT '输入哈希',
    source_size BIGINT NULL COMMENT '输入字节数',
    source_content_type VARCHAR(120) NULL COMMENT '输入类型',
    after_chapter_id BIGINT NULL COMMENT '插入位置之前的章节',
    total_units INT NOT NULL DEFAULT 0 COMMENT '总处理单元数',
    completed_units INT NOT NULL DEFAULT 0 COMMENT '完成单元数',
    failed_units INT NOT NULL DEFAULT 0 COMMENT '失败单元数',
    current_unit VARCHAR(160) NULL COMMENT '当前处理单元',
    last_error VARCHAR(2000) NULL COMMENT '最近错误',
    attempt_count INT NOT NULL DEFAULT 0 COMMENT '尝试次数',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    started_at TIMESTAMP NULL COMMENT '开始时间',
    finished_at TIMESTAMP NULL COMMENT '完成时间',
    created_by VARCHAR(64) NOT NULL COMMENT '创建人标识',
    updated_by VARCHAR(64) NOT NULL COMMENT '更新人标识',
    PRIMARY KEY (id),
    INDEX idx_manga_task_owner_status (owner_user_id, status, created_at),
    CONSTRAINT fk_manga_task_owner FOREIGN KEY (owner_user_id) REFERENCES manga_user (id) ON DELETE CASCADE,
    CONSTRAINT fk_manga_task_project FOREIGN KEY (project_id) REFERENCES manga_project (id) ON DELETE CASCADE
) COMMENT = '统一后台任务表';

CREATE TABLE IF NOT EXISTS manga_task_unit (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '任务单元主键',
    task_id BIGINT NOT NULL COMMENT '所属任务',
    unit_index INT NOT NULL COMMENT '输入顺序',
    chapter_id BIGINT NULL COMMENT '已创建章节主键',
    chapter_title VARCHAR(120) NOT NULL COMMENT '章节标题',
    status VARCHAR(24) NOT NULL COMMENT '单元状态',
    error_message VARCHAR(2000) NULL COMMENT '错误摘要',
    started_at TIMESTAMP NULL COMMENT '开始时间',
    finished_at TIMESTAMP NULL COMMENT '完成时间',
    PRIMARY KEY (id),
    CONSTRAINT uk_manga_task_unit_index UNIQUE (task_id, unit_index),
    INDEX idx_manga_task_unit_task_status (task_id, status),
    CONSTRAINT fk_manga_task_unit_task FOREIGN KEY (task_id) REFERENCES manga_task (id) ON DELETE CASCADE,
    CONSTRAINT fk_manga_task_unit_chapter FOREIGN KEY (chapter_id) REFERENCES manga_project_script_chapter (id) ON DELETE SET NULL
) COMMENT = '后台任务处理单元表';

CREATE TABLE IF NOT EXISTS manga_storyboard_shot (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '分镜镜头主键',
    project_id BIGINT NOT NULL COMMENT '所属项目主键',
    chapter_id BIGINT NULL COMMENT '关联剧本章节主键',
    sort_order INT NOT NULL COMMENT '项目内排序序号',
    shot_number VARCHAR(20) NOT NULL COMMENT '项目内展示镜号',
    title VARCHAR(120) NOT NULL COMMENT '分镜标题',
    scene_name VARCHAR(120) NULL COMMENT '所属场景名称',
    shot_type VARCHAR(32) NULL COMMENT '景别类型',
    camera_movement VARCHAR(64) NULL COMMENT '镜头运动方式',
    duration_seconds INT NOT NULL DEFAULT 0 COMMENT '预计时长，单位为秒',
    content VARCHAR(2000) NULL COMMENT '画面内容描述',
    dialogue VARCHAR(2000) NULL COMMENT '角色对白或旁白',
    sound_effect VARCHAR(500) NULL COMMENT '音效与环境声说明',
    image_url VARCHAR(1024) NULL COMMENT '分镜参考图片地址',
    notes VARCHAR(2000) NULL COMMENT '制作备注',
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT' COMMENT '分镜制作状态',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后修改时间',
    created_by VARCHAR(64) NOT NULL COMMENT '创建人标识',
    updated_by VARCHAR(64) NOT NULL COMMENT '最后修改人标识',
    PRIMARY KEY (id),
    INDEX idx_manga_storyboard_project_order (project_id, sort_order),
    INDEX idx_manga_storyboard_project_number (project_id, shot_number),
    CONSTRAINT fk_manga_storyboard_project FOREIGN KEY (project_id) REFERENCES manga_project (id) ON DELETE CASCADE
) COMMENT = '项目分镜镜头表';

CREATE TABLE IF NOT EXISTS manga_ai_provider_config (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT 'AI 服务配置主键',
    owner_user_id BIGINT NOT NULL COMMENT '配置所有者用户主键',
    name VARCHAR(80) NOT NULL COMMENT '用户自定义配置名称',
    provider_type VARCHAR(32) NOT NULL COMMENT 'AI 服务商或兼容协议类型',
    base_url VARCHAR(1024) NOT NULL COMMENT 'AI 服务 API 根地址',
    default_model VARCHAR(160) NULL COMMENT '未来调用时默认使用的模型编码',
    api_key_ciphertext TEXT NULL COMMENT 'AES-GCM 加密后的 API Key 密文',
    api_key_hint VARCHAR(32) NULL COMMENT '用于界面确认的 API Key 脱敏摘要',
    proxy_type VARCHAR(16) NOT NULL DEFAULT 'NONE' COMMENT '出站代理类型：NONE、HTTP、SOCKS5',
    proxy_host VARCHAR(255) NULL COMMENT '出站代理主机',
    proxy_port INT NULL COMMENT '出站代理端口',
    proxy_username VARCHAR(255) NULL COMMENT '出站代理认证用户名',
    proxy_password_ciphertext TEXT NULL COMMENT 'AES-GCM 加密后的代理密码密文',
    proxy_password_hint VARCHAR(32) NULL COMMENT '用于界面确认的代理密码脱敏摘要',
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
