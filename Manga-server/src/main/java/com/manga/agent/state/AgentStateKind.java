package com.manga.agent.state;

/**
 * 区分 AgentScope 单对象状态和列表状态的持久化编码。
 */
public enum AgentStateKind {
    /** 单个 State 对象。 */
    SINGLE,
    /** State 对象列表。 */
    LIST
}
