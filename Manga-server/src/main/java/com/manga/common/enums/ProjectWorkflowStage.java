package com.manga.common.enums;

/** 定义项目创作工作流的当前推荐阶段。 */
public enum ProjectWorkflowStage {
    /** 剧本编辑与导入。 */
    SCRIPT,
    /** 分镜编排。 */
    STORYBOARD,
    /** 角色和场景素材。 */
    ASSETS,
    /** 图片和视频生成。 */
    GENERATION,
    /** 合成与导出。 */
    EXPORT
}
