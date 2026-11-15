ALTER TABLE manga_creative_run
    ADD COLUMN auto_approve_risky_changes BOOLEAN NOT NULL DEFAULT FALSE
    COMMENT '本轮是否预授权覆盖章节和提交媒体生成；权限仍限于绑定项目';
