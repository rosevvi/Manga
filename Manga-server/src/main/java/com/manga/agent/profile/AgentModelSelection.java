package com.manga.agent.profile;

/** Profile 允许的模型选择方式；所有配置仍属于发起 Run 的用户。 */
public enum AgentModelSelection {
    /** 根调用使用用户默认模型，Worker 继承父模型。 */ USER_DEFAULT,
    /** 根调用可显式选择用户模型或使用默认模型，Worker 继承父模型。 */ USER_SELECTABLE,
    /** 只能作为 Worker 使用父步骤已冻结的模型。 */ INHERIT_PARENT
}
