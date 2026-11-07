ALTER TABLE manga_creative_step ADD COLUMN delegation_plan_id CHAR(36) NULL COMMENT '所属委派计划，根步骤为空';
ALTER TABLE manga_creative_step ADD COLUMN node_key VARCHAR(48) NULL COMMENT '计划内稳定节点名，汇合节点使用系统名称';
ALTER TABLE manga_creative_step ADD COLUMN delegation_depth INT NOT NULL DEFAULT 0 COMMENT '委派树深度，根为零';
ALTER TABLE manga_creative_step ADD COLUMN delegation_round INT NOT NULL DEFAULT 0 COMMENT '已物化的委派轮数';
ALTER TABLE manga_creative_step ADD COLUMN result_json LONGTEXT NULL COMMENT '通过 schema 校验的不可变 Worker 结果';
ALTER TABLE manga_creative_step ADD COLUMN continuation_json LONGTEXT NULL COMMENT '父步骤恢复时的有界子图摘要及结果引用，不保存候选正文';
ALTER TABLE manga_creative_step ADD COLUMN reserved_epoch BIGINT NOT NULL DEFAULT -1 COMMENT '已预留模型预算的最近 owner epoch';

CREATE TABLE manga_creative_delegation (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '委派检查点主键',
    plan_id CHAR(36) NOT NULL COMMENT '稳定计划标识',
    run_id CHAR(36) NOT NULL COMMENT '所属 Run',
    parent_step_id CHAR(36) NOT NULL COMMENT '委派方步骤',
    trigger_tool_call_id VARCHAR(128) NOT NULL COMMENT 'SDK 提供的工具调用标识',
    owner_epoch BIGINT NOT NULL COMMENT '提交计划的执行代次',
    round_no INT NOT NULL COMMENT '该父步骤的计划轮数',
    title VARCHAR(120) NOT NULL COMMENT '展示给用户的计划名称',
    status VARCHAR(32) NOT NULL COMMENT 'DRAFT MATERIALIZED COMPLETED FAILED CANCELLED',
    frozen_json LONGTEXT NOT NULL COMMENT '经过校验的 DAG 与冻结 Worker Blueprint，不含密钥',
    request_fingerprint CHAR(64) NOT NULL COMMENT '计划内容指纹',
    join_step_id CHAR(36) NULL COMMENT '物化后的 ALL_SUCCESS 汇合步骤',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    created_by VARCHAR(128) NOT NULL COMMENT '创建操作者',
    updated_by VARCHAR(128) NOT NULL COMMENT '更新操作者',
    PRIMARY KEY (id),
    CONSTRAINT uk_delegation_plan UNIQUE (plan_id),
    CONSTRAINT uk_delegation_round UNIQUE (parent_step_id, round_no),
    CONSTRAINT uk_delegation_epoch UNIQUE (parent_step_id, owner_epoch),
    CONSTRAINT uk_delegation_call UNIQUE (parent_step_id, trigger_tool_call_id),
    CONSTRAINT ck_delegation_status CHECK (status IN ('DRAFT', 'MATERIALIZED', 'COMPLETED', 'FAILED', 'CANCELLED')),
    CONSTRAINT fk_delegation_run FOREIGN KEY (run_id) REFERENCES manga_creative_run (run_id),
    CONSTRAINT fk_delegation_parent FOREIGN KEY (parent_step_id) REFERENCES manga_creative_step (step_id),
    INDEX idx_delegation_reconcile (status, id),
    INDEX idx_delegation_run (run_id, id)
) COMMENT = '模型工具提交后可恢复物化的协作计划';

CREATE TABLE manga_creative_step_dependency (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '依赖边主键',
    run_id CHAR(36) NOT NULL COMMENT '两端共同所属 Run',
    upstream_step_id CHAR(36) NOT NULL COMMENT '必须成功的前置步骤',
    downstream_step_id CHAR(36) NOT NULL COMMENT '依赖该结果的后置步骤',
    join_policy VARCHAR(32) NOT NULL COMMENT '固定 ALL_SUCCESS，所有前置成功才继续',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    created_by VARCHAR(128) NOT NULL COMMENT '创建操作者',
    updated_by VARCHAR(128) NOT NULL COMMENT '更新操作者',
    PRIMARY KEY (id),
    CONSTRAINT uk_step_dependency UNIQUE (upstream_step_id, downstream_step_id),
    CONSTRAINT ck_step_dependency_distinct CHECK (upstream_step_id <> downstream_step_id),
    CONSTRAINT ck_step_dependency_policy CHECK (join_policy = 'ALL_SUCCESS'),
    CONSTRAINT fk_dependency_upstream FOREIGN KEY (upstream_step_id) REFERENCES manga_creative_step (step_id),
    CONSTRAINT fk_dependency_downstream FOREIGN KEY (downstream_step_id) REFERENCES manga_creative_step (step_id),
    INDEX idx_dependency_run (run_id, id),
    INDEX idx_dependency_downstream (downstream_step_id)
) COMMENT = '持久化调度依赖，父子归属不代表执行顺序';

CREATE TABLE manga_creative_run_budget (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '预算主键',
    run_id CHAR(36) NOT NULL COMMENT '所属 Run',
    max_steps INT NOT NULL COMMENT '步骤数量上限，含根和汇合',
    max_depth INT NOT NULL COMMENT '委派树深度上限',
    max_parallel INT NOT NULL COMMENT '同时执行租约数上限',
    max_repair_rounds INT NOT NULL COMMENT '首次计划以外的修订轮数上限',
    max_invocations INT NOT NULL COMMENT '含重试的模型执行次数上限',
    max_reserved_turns INT NOT NULL COMMENT '最大推理轮数累计预留上限，不代表实际 token 或费用',
    used_invocations INT NOT NULL DEFAULT 0 COMMENT '已预留模型执行次数',
    reserved_turns INT NOT NULL DEFAULT 0 COMMENT '已预留最大推理轮数',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    created_by VARCHAR(128) NOT NULL COMMENT '创建操作者',
    updated_by VARCHAR(128) NOT NULL COMMENT '更新操作者',
    PRIMARY KEY (id),
    CONSTRAINT uk_creative_run_budget UNIQUE (run_id),
    CONSTRAINT fk_budget_run FOREIGN KEY (run_id) REFERENCES manga_creative_run (run_id)
) COMMENT = '受理时冻结并由 Run 行锁串行扣减的协作预算';

ALTER TABLE manga_creative_step ADD CONSTRAINT uk_creative_step_plan_node UNIQUE (delegation_plan_id, node_key);
ALTER TABLE manga_creative_step ADD CONSTRAINT fk_creative_step_plan FOREIGN KEY (delegation_plan_id) REFERENCES manga_creative_delegation (plan_id);
