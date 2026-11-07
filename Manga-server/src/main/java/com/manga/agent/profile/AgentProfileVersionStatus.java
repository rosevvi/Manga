package com.manga.agent.profile;

/** 版本发布后不可编辑，回滚仅切换聚合发布指针。 */
public enum AgentProfileVersionStatus {
    /** 可编辑且不能被普通 Run 选择。 */ DRAFT,
    /** 已验证发布，内容不可变，可作为回滚目标。 */ PUBLISHED
}
