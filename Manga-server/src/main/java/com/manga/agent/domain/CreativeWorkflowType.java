package com.manga.agent.domain;

/**
 * 定义可独立版本化的创作工作流类型。
 */
public enum CreativeWorkflowType {

    /** 单 Director 助手对话工作流。 */
    ASSISTANT_CHAT,

    /** 针对指定章节生成分镜的工作流。 */
    CHAPTER_STORYBOARD;

    /** 根据稳定业务操作选择对应的工作流类型。 */
    public static CreativeWorkflowType fromOperation(CreativeOperationType operationType) {
        return switch (operationType) {
            case ASSISTANT_CHAT -> ASSISTANT_CHAT;
            case GENERATE_CHAPTER_STORYBOARD -> CHAPTER_STORYBOARD;
        };
    }
}
