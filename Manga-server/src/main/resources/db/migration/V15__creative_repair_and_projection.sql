ALTER TABLE manga_creative_step DROP CONSTRAINT ck_manga_creative_step_status;
ALTER TABLE manga_creative_step ADD CONSTRAINT ck_manga_creative_step_status CHECK
    (status IN ('PENDING','READY','RUNNING','WAITING_APPROVAL','WAITING_REPAIR_CONFIRMATION','WAITING_JOB',
               'WAITING_CHILDREN','WAITING_RETRY','COMPLETED','FAILED','CANCEL_REQUESTED','CANCELLED','SKIPPED'));
ALTER TABLE manga_creative_step ADD COLUMN read_result_step_ids_json LONGTEXT NULL
    COMMENT '已成功读取的跨轮次结果引用，用于保证修订基于旧候选和评审';

CREATE TABLE manga_creative_repair_decision (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '修订确认数据库主键',
    decision_id CHAR(36) NOT NULL COMMENT '对外修订确认标识',
    run_id CHAR(36) NOT NULL COMMENT '所属创作运行标识',
    parent_step_id CHAR(36) NOT NULL COMMENT '等待确认的父步骤',
    source_plan_id CHAR(36) NOT NULL COMMENT '评审所属的原计划',
    round_no INT NOT NULL COMMENT '原计划轮次',
    candidate_step_id CHAR(36) NOT NULL COMMENT '不可变原候选结果步骤',
    review_step_id CHAR(36) NOT NULL COMMENT '不可变原评审结果步骤',
    issues_json LONGTEXT NOT NULL COMMENT '评审问题列表JSON',
    base_project_revision BIGINT NOT NULL COMMENT '候选依据的项目版本',
    chapter_id BIGINT NULL COMMENT '可选目标章节',
    base_chapter_revision BIGINT NULL COMMENT '确认时需匹配的章节版本',
    remaining_execution_millis BIGINT NOT NULL COMMENT '暂停时剩余执行时间毫秒数',
    status VARCHAR(24) NOT NULL COMMENT '确认生命周期状态',
    action VARCHAR(24) NULL COMMENT '用户修订或结束命令',
    request_id VARCHAR(64) NULL COMMENT '用户决定的幂等请求标识',
    comment VARCHAR(2000) NULL COMMENT '用户补充修订意见',
    paused_at TIMESTAMP NOT NULL COMMENT '暂停起点',
    expires_at TIMESTAMP NOT NULL COMMENT '确认有效截止时间',
    decided_at TIMESTAMP NULL COMMENT '决定发生时间',
    repair_plan_id CHAR(36) NULL COMMENT '确认后唯一创建的修订计划',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '记录创建时间',
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '记录更新时间',
    created_by VARCHAR(128) NOT NULL COMMENT '创建操作者',
    updated_by VARCHAR(128) NOT NULL COMMENT '最近修改操作者',
    PRIMARY KEY (id), UNIQUE KEY uk_creative_repair_decision (decision_id),
    UNIQUE KEY uk_creative_repair_review (review_step_id),
    INDEX idx_creative_repair_run (run_id, id), INDEX idx_creative_repair_expiry (status, expires_at, id),
    CONSTRAINT fk_creative_repair_run FOREIGN KEY (run_id) REFERENCES manga_creative_run(run_id) ON DELETE CASCADE
) COMMENT='用户授权下一轮修订的持久化确认单';

CREATE TABLE manga_creative_message_projection (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '投影检查点数据库主键',
    run_id CHAR(36) NOT NULL COMMENT '唯一所属运行',
    projected_through_sequence BIGINT NOT NULL DEFAULT 0 COMMENT '消息原子提交覆盖的事件水位',
    owner_id VARCHAR(128) NULL COMMENT '领取投影批次的实例标识',
    lease_until TIMESTAMP NULL COMMENT '领取批次租约截止时间',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '记录创建时间',
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '记录更新时间',
    created_by VARCHAR(128) NOT NULL COMMENT '创建操作者',
    updated_by VARCHAR(128) NOT NULL COMMENT '最近修改操作者',
    PRIMARY KEY (id), UNIQUE KEY uk_creative_projection_run (run_id),
    CONSTRAINT fk_creative_projection_run FOREIGN KEY (run_id) REFERENCES manga_creative_run(run_id) ON DELETE CASCADE
) COMMENT='按运行隔离消息投影事务的检查点';

INSERT INTO manga_creative_message_projection(run_id, projected_through_sequence, created_by, updated_by)
SELECT run_id, projected_through_sequence, 'system:database-migration', 'system:database-migration'
FROM manga_creative_run;
