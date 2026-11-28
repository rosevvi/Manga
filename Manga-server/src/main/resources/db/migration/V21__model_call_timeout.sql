ALTER TABLE manga_ai_provider_config
    ADD COLUMN call_timeout VARCHAR(16) NOT NULL DEFAULT 'MINUTES_30' COMMENT '文本模型单次调用总时限，UNLIMITED 表示无单次固定时限';

ALTER TABLE manga_ai_provider_config
    ADD CONSTRAINT ck_ai_model_call_timeout CHECK (call_timeout IN (
        'MINUTES_5', 'MINUTES_15', 'MINUTES_30', 'MINUTES_60', 'HOURS_2', 'UNLIMITED'
    ));
