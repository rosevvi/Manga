-- 为已有 Manga 数据库增加项目与分镜模块，可在 DBeaver 中直接执行。
CREATE TABLE IF NOT EXISTS manga_project (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '项目主键',
    owner_user_id BIGINT NOT NULL COMMENT '项目所有者用户主键',
    name VARCHAR(120) NOT NULL COMMENT '项目名称',
    description VARCHAR(1000) NULL COMMENT '项目简介',
    cover_url VARCHAR(1024) NULL COMMENT '项目封面地址',
    genre VARCHAR(64) NULL COMMENT '作品类型',
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT' COMMENT '项目状态',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后修改时间',
    created_by VARCHAR(64) NOT NULL COMMENT '创建人标识',
    updated_by VARCHAR(64) NOT NULL COMMENT '最后修改人标识',
    PRIMARY KEY (id),
    INDEX idx_manga_project_owner_updated (owner_user_id, updated_at),
    CONSTRAINT fk_manga_project_owner FOREIGN KEY (owner_user_id) REFERENCES manga_user (id) ON DELETE CASCADE
) COMMENT = '漫剧创作项目表';

CREATE TABLE IF NOT EXISTS manga_storyboard_shot (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '分镜镜头主键',
    project_id BIGINT NOT NULL COMMENT '所属项目主键',
    sort_order INT NOT NULL COMMENT '项目内排序序号',
    shot_number VARCHAR(20) NOT NULL COMMENT '项目内展示镜号',
    title VARCHAR(120) NOT NULL COMMENT '分镜标题',
    scene_name VARCHAR(120) NULL COMMENT '所属场景名称',
    shot_type VARCHAR(32) NULL COMMENT '景别类型',
    camera_movement VARCHAR(64) NULL COMMENT '镜头运动方式',
    duration_seconds INT NOT NULL DEFAULT 0 COMMENT '预计时长，单位为秒',
    content VARCHAR(2000) NULL COMMENT '画面内容描述',
    dialogue VARCHAR(2000) NULL COMMENT '角色对白或旁白',
    sound_effect VARCHAR(500) NULL COMMENT '音效与环境声说明',
    image_url VARCHAR(1024) NULL COMMENT '分镜参考图片地址',
    notes VARCHAR(2000) NULL COMMENT '制作备注',
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT' COMMENT '分镜制作状态',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后修改时间',
    created_by VARCHAR(64) NOT NULL COMMENT '创建人标识',
    updated_by VARCHAR(64) NOT NULL COMMENT '最后修改人标识',
    PRIMARY KEY (id),
    INDEX idx_manga_storyboard_project_order (project_id, sort_order),
    INDEX idx_manga_storyboard_project_number (project_id, shot_number),
    CONSTRAINT fk_manga_storyboard_project FOREIGN KEY (project_id) REFERENCES manga_project (id) ON DELETE CASCADE
) COMMENT = '项目分镜镜头表';
