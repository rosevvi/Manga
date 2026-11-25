package com.manga.agent.domain;

/**
 * 定义创作运行承载的稳定业务操作类型。
 */
public enum CreativeOperationType {

    /** 与创作助手进行一次对话推理。 */
    ASSISTANT_CHAT,

    /** 读取项目剧本并生成结构化的专业解析报告。 */
    PARSE_SCRIPT,

    /** 为指定剧本章节生成分镜方案。 */
    GENERATE_CHAPTER_STORYBOARD,
    /** 为项目生成待审批的新章节提案。 */
    GENERATE_SCRIPT_CHAPTER,
    /** 为单个镜头提交待审批图片生成提案。 */
    GENERATE_SHOT_IMAGE,
    /** 为单个镜头提交视频提案。 */
    GENERATE_SHOT_VIDEO,
    /** 为项目资产图片项发起生成提案。 */
    GENERATE_ASSET_IMAGE;

    /** 页面已经提供结构化参数的操作，无需创建 Agent 会话。 */
    public boolean usesManualMediaProposal() {
        return this == GENERATE_SHOT_VIDEO || this == GENERATE_ASSET_IMAGE;
    }
}
