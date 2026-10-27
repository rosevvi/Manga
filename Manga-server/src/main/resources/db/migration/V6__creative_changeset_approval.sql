ALTER TABLE manga_project ADD COLUMN revision BIGINT NOT NULL DEFAULT 1 COMMENT '项目业务版本，每次编辑递增用于提案冲突检测';
ALTER TABLE manga_project_script_chapter ADD COLUMN revision BIGINT NOT NULL DEFAULT 1 COMMENT '章节内容与分镜业务版本';
ALTER TABLE manga_project_script_scene ADD COLUMN created_by VARCHAR(128) NOT NULL DEFAULT 'system:database-migration' COMMENT '场景创建操作者';
ALTER TABLE manga_project_script_scene ADD COLUMN updated_by VARCHAR(128) NOT NULL DEFAULT 'system:database-migration' COMMENT '场景最后更新操作者';
ALTER TABLE manga_project_script_dialogue ADD COLUMN created_by VARCHAR(128) NOT NULL DEFAULT 'system:database-migration' COMMENT '对白创建操作者';
ALTER TABLE manga_project_script_dialogue ADD COLUMN updated_by VARCHAR(128) NOT NULL DEFAULT 'system:database-migration' COMMENT '对白最后更新操作者';
ALTER TABLE manga_creative_step ADD COLUMN base_project_revision BIGINT NULL COMMENT '受理时冻结的项目版本，提案不得覆盖之后发生的编辑';
ALTER TABLE manga_creative_step ADD COLUMN approval_result_json LONGTEXT NULL COMMENT '审批后供原 Blueprint 恢复的结构化结果';

CREATE TABLE manga_creative_change_set (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '提案数据库主键',
    change_set_id CHAR(36) NOT NULL COMMENT '稳定提案标识',
    run_id CHAR(36) NOT NULL COMMENT '来源创作运行',
    step_id CHAR(36) NOT NULL COMMENT '来源步骤，单步骤仅允许一个不可变提案',
    user_id BIGINT NOT NULL COMMENT '提案发起用户',
    project_id BIGINT NOT NULL COMMENT '目标项目',
    chapter_id BIGINT NULL COMMENT '目标章节，新建章节时为空',
    kind VARCHAR(32) NOT NULL COMMENT '提案类型 SCRIPT 或 STORYBOARD',
    schema_version INT NOT NULL COMMENT '提案 Schema 版本',
    instruction TEXT NOT NULL COMMENT '原始用户任务，用于冲突后重新生成，不包含历史上下文',
    status VARCHAR(32) NOT NULL COMMENT '提案生命周期状态',
    base_project_revision BIGINT NOT NULL COMMENT '开始生成前的项目版本',
    base_chapter_revision BIGINT NULL COMMENT '目标章节基础版本',
    before_json LONGTEXT NOT NULL COMMENT '预览差异使用的业务原文快照',
    payload_json LONGTEXT NOT NULL COMMENT '不可变且校验通过的提案正文',
    result_json LONGTEXT NULL COMMENT '应用结果和恢复命令',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    created_by VARCHAR(128) NOT NULL COMMENT '创建操作者',
    updated_by VARCHAR(128) NOT NULL COMMENT '更新操作者',
    PRIMARY KEY (id),
    CONSTRAINT uk_creative_change_set_id UNIQUE (change_set_id),
    CONSTRAINT uk_creative_change_set_step UNIQUE (step_id),
    CONSTRAINT ck_creative_change_set_kind CHECK (kind IN ('SCRIPT', 'STORYBOARD')),
    CONSTRAINT ck_creative_change_set_status CHECK (status IN ('DRAFT', 'PENDING', 'APPLIED', 'REJECTED', 'CONFLICTED', 'EXPIRED', 'CANCELLED')),
    INDEX idx_creative_change_set_run (run_id, id),
    CONSTRAINT fk_creative_change_set_run FOREIGN KEY (run_id) REFERENCES manga_creative_run (run_id) ON DELETE CASCADE,
    CONSTRAINT fk_creative_change_set_step FOREIGN KEY (step_id) REFERENCES manga_creative_step (step_id) ON DELETE CASCADE
) COMMENT = 'AI 业务变更提案与审计链';

CREATE TABLE manga_creative_approval (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '审批数据库主键',
    change_set_id CHAR(36) NOT NULL COMMENT '被审批的不可变提案',
    status VARCHAR(32) NOT NULL COMMENT '审批状态',
    expires_at TIMESTAMP NOT NULL COMMENT '审批截止时间',
    decided_by BIGINT NULL COMMENT '认证审批人，系统过期或取消时为空',
    decided_at TIMESTAMP NULL COMMENT '最终决定时间',
    comment VARCHAR(2000) NULL COMMENT '用户评论或系统原因',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    created_by VARCHAR(128) NOT NULL COMMENT '创建操作者',
    updated_by VARCHAR(128) NOT NULL COMMENT '更新操作者',
    PRIMARY KEY (id),
    CONSTRAINT uk_creative_approval_change_set UNIQUE (change_set_id),
    CONSTRAINT ck_creative_approval_status CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED', 'CONFLICTED', 'EXPIRED', 'CANCELLED')),
    INDEX idx_creative_approval_expiry (status, expires_at, id),
    CONSTRAINT fk_creative_approval_change_set FOREIGN KEY (change_set_id) REFERENCES manga_creative_change_set (change_set_id) ON DELETE CASCADE
) COMMENT = 'AI 提案用户审批与评论';
