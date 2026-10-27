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
    GENERATE_SCRIPT_CHAPTER
}
