CREATE TABLE manga_generation_media_object (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '生成对象登记主键',
    job_id CHAR(36) NOT NULL COMMENT '来源生成作业',
    owner_epoch BIGINT NOT NULL COMMENT '写入 worker 代次，每个代次使用不同物理对象',
    storage_type VARCHAR(32) NOT NULL COMMENT '存储策略编码',
    storage_namespace CHAR(64) NOT NULL COMMENT '磁盘根目录或 OSS 位置摘要，不包含密钥',
    object_name VARCHAR(128) NOT NULL COMMENT '服务端 UUID 与代次组合，不含路径和扩展名',
    url VARCHAR(2048) NOT NULL COMMENT '预先确定的平台访问地址',
    status VARCHAR(32) NOT NULL COMMENT 'PENDING PUBLISHED RETIRING DELETED',
    claim_owner CHAR(36) NULL COMMENT '本轮回收的随机令牌',
    claim_until TIMESTAMP NULL COMMENT '回收执行权截止时间',
    next_attempt_at TIMESTAMP NOT NULL COMMENT '下次回收或墓碑复查时间',
    attempts INT NOT NULL DEFAULT 0 COMMENT '回收累计尝试次数',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '写入前登记时间',
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后更新时间',
    created_by VARCHAR(128) NOT NULL COMMENT '创建操作者',
    updated_by VARCHAR(128) NOT NULL COMMENT '更新操作者',
    PRIMARY KEY (id),
    CONSTRAINT uk_generation_media_epoch UNIQUE (job_id, owner_epoch),
    CONSTRAINT uk_generation_media_location UNIQUE (storage_type, storage_namespace, object_name),
    CONSTRAINT ck_generation_media_status CHECK (status IN ('PENDING', 'PUBLISHED', 'RETIRING', 'DELETED')),
    CONSTRAINT fk_generation_media_job FOREIGN KEY (job_id) REFERENCES manga_generation_job (job_id),
    INDEX idx_generation_media_cleanup (status, next_attempt_at, claim_until, id)
) COMMENT = '物理写入前登记的生成对象与可恢复回收墓碑';

ALTER TABLE manga_creative_artifact ADD COLUMN media_object_id BIGINT NULL COMMENT '已发布物理对象，旧产物可为空且不参与自动回收';
ALTER TABLE manga_creative_artifact ADD CONSTRAINT uk_artifact_media_object UNIQUE (media_object_id);
ALTER TABLE manga_creative_artifact ADD CONSTRAINT fk_artifact_media_object FOREIGN KEY (media_object_id) REFERENCES manga_generation_media_object (id);
ALTER TABLE manga_creative_artifact ADD COLUMN adoption_request_revision BIGINT NULL COMMENT '最近采用请求预览的项目版本，用于重放去重';
ALTER TABLE manga_creative_artifact ADD COLUMN adopted_project_revision BIGINT NULL COMMENT '最近采用成功后的项目版本';
ALTER TABLE manga_creative_artifact ADD COLUMN adopted_at TIMESTAMP NULL COMMENT '最近采用时间';
ALTER TABLE manga_creative_artifact ADD COLUMN adopted_by BIGINT NULL COMMENT '最近主动采用产物的认证用户';
ALTER TABLE manga_storyboard_shot ADD COLUMN image_artifact_id BIGINT NULL COMMENT '当前采用图片的产物引用，手动改换地址时清除';
ALTER TABLE manga_storyboard_shot ADD CONSTRAINT fk_shot_image_artifact FOREIGN KEY (image_artifact_id) REFERENCES manga_creative_artifact (id);
