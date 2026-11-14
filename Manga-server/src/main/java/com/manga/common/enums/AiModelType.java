package com.manga.common.enums;

/** 用户模型配置的用途，每类可以独立设为默认。 */
public enum AiModelType {
    /** 剧本、分镜和对话推理。 */
    TEXT,
    /** 静态图像生成。 */
    IMAGE,
    /** 视频片段生成。 */
    VIDEO
}
