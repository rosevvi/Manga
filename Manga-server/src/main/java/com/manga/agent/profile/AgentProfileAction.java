package com.manga.agent.profile;

/** 可追溯的配置生命周期操作。 */
public enum AgentProfileAction {
    /** 初次登记内置定义。 */ INITIALIZED,
    /** 创建新专业 Agent。 */ CREATED,
    /** 从当前或历史版本创建草稿。 */ DRAFT_CREATED,
    /** 修改尚未发布的草稿。 */ DRAFT_UPDATED,
    /** 发布通过校验的版本。 */ PUBLISHED,
    /** 发布指针切回已有发布版本。 */ ROLLED_BACK,
    /** 允许新调用使用。 */ ENABLED,
    /** 禁止新调用使用，已有快照不受影响。 */ DISABLED
}
