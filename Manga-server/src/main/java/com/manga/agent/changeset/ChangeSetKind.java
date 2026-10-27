package com.manga.agent.changeset;

/** 可审批的业务变更类型；分镜提案只追加草稿，不删除已有镜头。 */
public enum ChangeSetKind {
    /** 新建或完整替换一个章节。 */
    SCRIPT,
    /** 在一个章节下批量追加分镜草稿。 */
    STORYBOARD
}
