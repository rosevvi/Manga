package com.manga.agent.domain;

/**
 * 标识创作请求使用的运行时实现。
 */
public enum CreativeRuntimeType {

    /** 现有 AgentScope 单 Agent 运行时。 */
    LEGACY,

    /** 新一代可恢复创作运行时，对应 Durable Execution。 */
    DURABLE
}
