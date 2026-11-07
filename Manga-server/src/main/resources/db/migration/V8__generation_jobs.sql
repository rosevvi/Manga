-- 图片作业、唯一提交意图、供应商容量与平台产物；不保存 API Key。
ALTER TABLE manga_creative_change_set DROP CONSTRAINT ck_creative_change_set_kind;
ALTER TABLE manga_creative_change_set ADD CONSTRAINT ck_creative_change_set_kind CHECK (kind IN ('SCRIPT', 'STORYBOARD', 'IMAGE_GENERATION'));
ALTER TABLE manga_creative_step ADD COLUMN generation_result_json TEXT NULL COMMENT '外部作业完成后供原 Blueprint 恢复的结构化结果';

CREATE TABLE manga_generation_job (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '数据库主键',
    job_id CHAR(36) NOT NULL COMMENT '平台稳定作业标识，也是提交意图幂等键',
    run_id CHAR(36) NOT NULL COMMENT '来源运行',
    step_id CHAR(36) NOT NULL COMMENT '等待作业的步骤',
    change_set_id CHAR(36) NOT NULL COMMENT '批准的唯一图片提案',
    user_id BIGINT NOT NULL COMMENT '作业归属用户',
    project_id BIGINT NOT NULL COMMENT '归属项目',
    shot_id BIGINT NOT NULL COMMENT '目标镜头',
    provider VARCHAR(32) NOT NULL COMMENT '供应商路由',
    config_id BIGINT NULL COMMENT '用户供应商配置，Fake 为空',
    config_snapshot_json TEXT NULL COMMENT '不含凭据的端点快照，防止审批后被换址',
    request_json TEXT NOT NULL COMMENT '冻结且已批准的生成参数',
    status VARCHAR(32) NOT NULL COMMENT '平台作业状态',
    provider_job_id VARCHAR(128) NULL COMMENT '异步供应商作业标识，同步 Images API 为空',
    progress INT NULL COMMENT '真实百分比，未知时为空',
    cancel_requested BOOLEAN NOT NULL COMMENT '已撤销结果采用权；不承诺同步供应商停止计费',
    result_base64 LONGTEXT NULL COMMENT '已生成 PNG 暂存数据；转存后清除',
    error_code VARCHAR(128) NULL COMMENT '公开错误编码',
    error_message VARCHAR(500) NULL COMMENT '脱敏错误摘要',
    owner_instance_id VARCHAR(128) NULL COMMENT '作业 worker 所属实例',
    owner_epoch BIGINT NOT NULL COMMENT '执行权围栏代次',
    lease_until TIMESTAMP NULL COMMENT 'worker 执行权截止',
    attempts INT NOT NULL COMMENT '状态读取或转存尝试次数',
    next_action_at TIMESTAMP NOT NULL COMMENT '下次允许处理时间',
    submitted_at TIMESTAMP NULL COMMENT '首次登记网络提交意图时间',
    finished_at TIMESTAMP NULL COMMENT '平台终态时间',
    reconciled_by BIGINT NULL COMMENT '主动核对未知提交的用户',
    reconciled_at TIMESTAMP NULL COMMENT '核对时间',
    reconciliation_note VARCHAR(2000) NULL COMMENT '人工核对说明，不是供应商取消确认',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    created_by VARCHAR(128) NOT NULL COMMENT '创建操作者',
    updated_by VARCHAR(128) NOT NULL COMMENT '最后更新操作者',
    PRIMARY KEY (id),
    CONSTRAINT uk_generation_job_id UNIQUE (job_id),
    CONSTRAINT uk_generation_job_proposal UNIQUE (change_set_id),
    CONSTRAINT uk_generation_external_job UNIQUE (provider, provider_job_id),
    CONSTRAINT ck_generation_progress CHECK (progress IS NULL OR progress BETWEEN 0 AND 100),
    CONSTRAINT ck_generation_job_status CHECK (status IN ('PENDING_SUBMISSION', 'SUBMITTING', 'SUBMITTED', 'RUNNING', 'MATERIALIZING', 'SUCCEEDED', 'FAILED', 'CANCELLED', 'UNKNOWN')),
    CONSTRAINT ck_generation_job_provider CHECK (provider IN ('FAKE', 'OPENAI')),
    CONSTRAINT ck_generation_job_attempts CHECK (attempts >= 0 AND owner_epoch >= 0),
    INDEX idx_generation_due (status, next_action_at, lease_until),
    INDEX idx_generation_owner (user_id, project_id, id),
    INDEX idx_generation_run (run_id, id),
    INDEX idx_generation_capacity (provider, status, user_id),
    CONSTRAINT fk_generation_run FOREIGN KEY (run_id) REFERENCES manga_creative_run (run_id),
    CONSTRAINT fk_generation_step FOREIGN KEY (step_id) REFERENCES manga_creative_step (step_id),
    CONSTRAINT fk_generation_proposal FOREIGN KEY (change_set_id) REFERENCES manga_creative_change_set (change_set_id)
) COMMENT = '独立于 Harness 的外部图片生成作业';

CREATE TABLE manga_generation_submission_outbox (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '提交意图主键',
    job_id CHAR(36) NOT NULL COMMENT '稳定平台作业 ID，也是提交去重键',
    status VARCHAR(32) NOT NULL COMMENT 'PENDING CLAIMED DONE UNKNOWN；UNKNOWN 禁止重投',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    created_by VARCHAR(128) NOT NULL COMMENT '创建操作者',
    updated_by VARCHAR(128) NOT NULL COMMENT '最后更新操作者',
    PRIMARY KEY (id),
    CONSTRAINT uk_generation_submit_job UNIQUE (job_id),
    CONSTRAINT ck_generation_submit_status CHECK (status IN ('PENDING', 'CLAIMED', 'DONE', 'UNKNOWN')),
    CONSTRAINT fk_generation_submit_job FOREIGN KEY (job_id) REFERENCES manga_generation_job (job_id)
) COMMENT = '审批事务写入的唯一提交意图，claim 和 lease 保存在关联 Job';

CREATE TABLE manga_generation_capacity (
    provider VARCHAR(32) NOT NULL COMMENT '供应商枚举，锁行保证跨实例并发上限',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    created_by VARCHAR(128) NOT NULL COMMENT '创建操作者',
    updated_by VARCHAR(128) NOT NULL COMMENT '最后更新操作者',
    PRIMARY KEY (provider)
) COMMENT = '供应商并发准入锁，未知付费作业继续占用容量';
INSERT INTO manga_generation_capacity (provider, created_by, updated_by) VALUES
    ('FAKE', 'system:database-migration', 'system:database-migration'),
    ('OPENAI', 'system:database-migration', 'system:database-migration');

CREATE TABLE manga_creative_artifact (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '产物主键',
    job_id CHAR(36) NOT NULL COMMENT '生成作业，一次生成只接收一张图片',
    run_id CHAR(36) NOT NULL COMMENT '来源运行',
    step_id CHAR(36) NOT NULL COMMENT '来源步骤',
    change_set_id CHAR(36) NOT NULL COMMENT '已批准的生成提案',
    user_id BIGINT NOT NULL COMMENT '产物所属用户',
    project_id BIGINT NOT NULL COMMENT '所属项目',
    shot_id BIGINT NOT NULL COMMENT '目标镜头，保存产物不覆盖人工镜头编辑',
    url VARCHAR(2048) NOT NULL COMMENT '转存后的平台地址，不是供应商临时链接',
    media_type VARCHAR(64) NOT NULL COMMENT '媒体 MIME 类型',
    byte_size BIGINT NOT NULL COMMENT '文件字节数',
    sha256 CHAR(64) NOT NULL COMMENT '转存内容摘要',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    created_by VARCHAR(128) NOT NULL COMMENT '创建操作者',
    updated_by VARCHAR(128) NOT NULL COMMENT '最后更新操作者',
    PRIMARY KEY (id),
    CONSTRAINT uk_creative_artifact_job UNIQUE (job_id),
    INDEX idx_creative_artifact_project (user_id, project_id, id),
    CONSTRAINT fk_creative_artifact_job FOREIGN KEY (job_id) REFERENCES manga_generation_job (job_id)
) COMMENT = '图片产物及 Run Step ChangeSet 审批追溯引用';

CREATE TABLE manga_generation_callback_inbox (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '回调事实主键',
    provider VARCHAR(32) NOT NULL COMMENT '已验证签名的来源供应商',
    event_id VARCHAR(128) NOT NULL COMMENT '中继稳定事件 ID，重发不得变化',
    provider_job_id VARCHAR(128) NOT NULL COMMENT '供应商作业标识，可先于 Job 绑定到达',
    payload_sha256 CHAR(64) NOT NULL COMMENT '已验签原始正文摘要，防止同事件更换内容',
    status VARCHAR(32) NOT NULL COMMENT 'PENDING PROCESSED IGNORED；只唤醒查询不直接应用结果',
    next_attempt_at TIMESTAMP NOT NULL COMMENT '未绑定作业时的下次消费时间，避免队首阻塞',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '首次接收时间',
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后处理时间',
    created_by VARCHAR(128) NOT NULL COMMENT '创建操作者',
    updated_by VARCHAR(128) NOT NULL COMMENT '最后更新操作者',
    PRIMARY KEY (id),
    CONSTRAINT uk_generation_callback_event UNIQUE (provider, event_id),
    CONSTRAINT ck_generation_callback_status CHECK (status IN ('PENDING', 'PROCESSED', 'IGNORED')),
    INDEX idx_generation_callback_pending (status, next_attempt_at, id)
) COMMENT = '签名回调 Inbox，容忍重复、乱序、先于提交结果到达';

