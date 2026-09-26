package com.manga.agent.domain;

/**
 * 定义创作运行承载的稳定业务操作类型。
 */
public enum CreativeOperationType {

    /** 与创作助手进行一次对话推理。 */
    ASSISTANT_CHAT,

    /** 为指定剧本章节生成分镜方案。 */
    GENERATE_CHAPTER_STORYBOARD
}
