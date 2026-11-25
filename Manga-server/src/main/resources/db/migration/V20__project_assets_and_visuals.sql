CREATE TABLE manga_project_asset (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '数据库主键',
    project_id BIGINT NOT NULL COMMENT '所属项目',
    asset_kind VARCHAR(16) NOT NULL COMMENT '资产类别',
    display_name VARCHAR(120) NOT NULL COMMENT '资产名称',
    overview VARCHAR(2000) NULL COMMENT '资产概述',
    role_label VARCHAR(40) NULL COMMENT '角色定位',
    identity_description VARCHAR(300) NULL COMMENT '角色身份',
    age_group VARCHAR(40) NULL COMMENT '角色年龄段',
    appearance_description VARCHAR(800) NULL COMMENT '角色外观特征',
    costume_description VARCHAR(800) NULL COMMENT '角色服饰风格',
    location_description VARCHAR(300) NULL COMMENT '场景地点',
    environment_description VARCHAR(800) NULL COMMENT '场景环境',
    lighting_description VARCHAR(300) NULL COMMENT '场景光照',
    material_description VARCHAR(300) NULL COMMENT '道具材质',
    primary_visual_id BIGINT NULL COMMENT '设为基准图的图片项',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    created_by VARCHAR(128) NOT NULL COMMENT '创建操作者',
    updated_by VARCHAR(128) NOT NULL COMMENT '更新操作者',
    PRIMARY KEY (id),
    CONSTRAINT fk_project_asset_project FOREIGN KEY (project_id) REFERENCES manga_project (id) ON DELETE CASCADE,
    INDEX idx_project_asset_kind (project_id, asset_kind, id),
    CONSTRAINT ck_project_asset_kind CHECK (asset_kind IN ('CHARACTER', 'SCENE', 'PROP'))
) COMMENT = '项目级角色、场景或道具，可由多章复用。';

CREATE TABLE manga_script_chapter_asset_ref (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '数据库主键',
    chapter_id BIGINT NOT NULL COMMENT '引用资产的章节',
    project_asset_id BIGINT NOT NULL COMMENT '已加入章节的项目资产',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    created_by VARCHAR(128) NOT NULL COMMENT '创建操作者',
    updated_by VARCHAR(128) NOT NULL COMMENT '更新操作者',
    PRIMARY KEY (id),
    CONSTRAINT uk_chapter_asset_ref UNIQUE (chapter_id, project_asset_id),
    INDEX idx_chapter_asset_lookup (project_asset_id),
    CONSTRAINT fk_chapter_asset_ref_chapter FOREIGN KEY (chapter_id) REFERENCES manga_project_script_chapter (id) ON DELETE CASCADE,
    CONSTRAINT fk_chapter_asset_ref_asset FOREIGN KEY (project_asset_id) REFERENCES manga_project_asset (id) ON DELETE CASCADE
) COMMENT = '章节与项目资产的可复用关联。';

CREATE TABLE manga_asset_visual (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '数据库主键',
    project_asset_id BIGINT NOT NULL COMMENT '所属项目资产',
    visual_kind VARCHAR(24) NOT NULL COMMENT '图片类型及所属分组',
    display_name VARCHAR(120) NOT NULL COMMENT '图片名称',
    current_artifact_id BIGINT NULL COMMENT '当前图片产物，生成成功后替换',
    prompt VARCHAR(500) NULL COMMENT '图片提示词',
    negative_prompt VARCHAR(300) NULL COMMENT '负面提示词',
    image_width INT NULL COMMENT '输出宽度像素',
    image_height INT NULL COMMENT '输出高度像素',
    provider VARCHAR(24) NULL COMMENT '图片供应商',
    model_config_id BIGINT NULL COMMENT '当前选择的模型配置',
    model_name VARCHAR(100) NULL COMMENT '生成模型名称',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    created_by VARCHAR(128) NOT NULL COMMENT '创建操作者',
    updated_by VARCHAR(128) NOT NULL COMMENT '更新操作者',
    PRIMARY KEY (id),
    INDEX idx_asset_visual_kind (project_asset_id, visual_kind, id),
    CONSTRAINT fk_asset_visual_asset FOREIGN KEY (project_asset_id) REFERENCES manga_project_asset (id) ON DELETE CASCADE,
    CONSTRAINT fk_asset_visual_artifact FOREIGN KEY (current_artifact_id) REFERENCES manga_creative_artifact (id)
) COMMENT = '资产的一项图片及其当前产物。';

CREATE TABLE manga_asset_visual_reference (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '数据库主键',
    visual_id BIGINT NOT NULL COMMENT '使用参考图的图片项',
    reference_visual_id BIGINT NOT NULL COMMENT '被引用的图片项',
    display_order INT NOT NULL COMMENT '参考图顺序',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    created_by VARCHAR(128) NOT NULL COMMENT '创建操作者',
    updated_by VARCHAR(128) NOT NULL COMMENT '更新操作者',
    PRIMARY KEY (id),
    CONSTRAINT uk_visual_reference_pair UNIQUE (visual_id, reference_visual_id),
    INDEX idx_visual_reference_order (visual_id, display_order),
    CONSTRAINT fk_visual_reference_owner FOREIGN KEY (visual_id) REFERENCES manga_asset_visual (id) ON DELETE CASCADE,
    CONSTRAINT fk_visual_reference_target FOREIGN KEY (reference_visual_id) REFERENCES manga_asset_visual (id) ON DELETE CASCADE,
    CONSTRAINT ck_visual_reference_self CHECK (visual_id <> reference_visual_id)
) COMMENT = '图片项之间的有序参考关系，执行时读取当前产物。';

ALTER TABLE manga_generation_job
    MODIFY COLUMN shot_id BIGINT NULL COMMENT '镜头生成的目标镜头';
ALTER TABLE manga_generation_job
    ADD COLUMN target_visual_id BIGINT NULL COMMENT '资产图生成的目标图片项';
ALTER TABLE manga_generation_job
    ADD CONSTRAINT ck_generation_job_target CHECK (
        (shot_id IS NOT NULL AND target_visual_id IS NULL) OR
        (shot_id IS NULL AND target_visual_id IS NOT NULL)
    );
CREATE INDEX idx_generation_job_visual ON manga_generation_job (target_visual_id, id);

ALTER TABLE manga_creative_artifact
    MODIFY COLUMN shot_id BIGINT NULL COMMENT '镜头产物的来源镜头，资产图产物为空';
