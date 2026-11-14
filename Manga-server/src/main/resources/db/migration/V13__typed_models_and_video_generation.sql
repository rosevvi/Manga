ALTER TABLE manga_ai_provider_config
    ADD COLUMN model_type VARCHAR(16) NOT NULL DEFAULT 'TEXT' COMMENT '模型用途：TEXT、IMAGE 或 VIDEO';

UPDATE manga_ai_provider_config SET model_type = 'IMAGE'
WHERE provider_type IN ('OPENAI', 'OPENAI_COMPATIBLE', 'NEWAPI') AND default_model = 'gpt-image-2';

ALTER TABLE manga_ai_provider_config
    ADD CONSTRAINT ck_ai_config_model_type CHECK (model_type IN ('TEXT', 'IMAGE', 'VIDEO'));
CREATE INDEX idx_ai_config_typed_default ON manga_ai_provider_config (owner_user_id, model_type, default_config, enabled);

ALTER TABLE manga_creative_change_set DROP CONSTRAINT ck_creative_change_set_kind;
ALTER TABLE manga_creative_change_set ADD CONSTRAINT ck_creative_change_set_kind
    CHECK (kind IN ('SCRIPT', 'STORYBOARD', 'IMAGE_GENERATION', 'VIDEO_GENERATION'));

ALTER TABLE manga_generation_job DROP CONSTRAINT ck_generation_job_provider;
ALTER TABLE manga_generation_job ADD CONSTRAINT ck_generation_job_provider
    CHECK (provider IN ('FAKE', 'OPENAI', 'AGNES'));
ALTER TABLE manga_generation_job ADD COLUMN result_url VARCHAR(4096) NULL
    COMMENT '视频待转存地址，只在内部作业保存，终态清除';
ALTER TABLE manga_generation_media_object ADD COLUMN media_format VARCHAR(8) NOT NULL DEFAULT 'PNG'
    COMMENT '已登记物理文件格式，用于写入与回收';

INSERT INTO manga_generation_capacity (provider, created_by, updated_by)
VALUES ('AGNES', 'system:database-migration', 'system:database-migration');
