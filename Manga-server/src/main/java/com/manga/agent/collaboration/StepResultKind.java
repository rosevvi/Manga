package com.manga.agent.collaboration;

/** Worker 输出的结构化业务类型。 */
public enum StepResultKind {
    /** 剧本或项目分析。 */ ANALYSIS,
    /** 尚未写入业务表的章节候选。 */ SCRIPT_DRAFT,
    /** 尚未写入业务表的分镜候选。 */ STORYBOARD_DRAFT,
    /** 一致性评审结论。 */ REVIEW
}
