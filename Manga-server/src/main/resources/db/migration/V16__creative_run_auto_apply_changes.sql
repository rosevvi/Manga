ALTER TABLE manga_creative_run
    ADD COLUMN auto_apply_changes BOOLEAN NOT NULL DEFAULT FALSE
    COMMENT '用户在本轮授权自动应用章节和分镜提案；媒体生成与修订仍需单独确认';
