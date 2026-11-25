package com.manga.agent.domain;

/**
 * 定义一次创作操作直接作用的业务资源类型。
 */
public enum CreativeResourceType {

    /** 漫剧项目。 */
    PROJECT,

    /** 项目剧本。 */
    SCRIPT,

    /** 剧本章节。 */
    CHAPTER,

    /** 单个分镜镜头。 */
    STORYBOARD_SHOT,
    /** 项目资产的独立图片项。 */
    ASSET_VISUAL
}
