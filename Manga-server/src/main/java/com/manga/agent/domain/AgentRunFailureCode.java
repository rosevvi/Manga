package com.manga.agent.domain;

/**
 * 定义 Legacy Agent Run 的稳定失败编码。
 */
public enum AgentRunFailureCode {

    /** AgentScope 事件流执行失败。 */
    AGENT_EXECUTION_FAILED,

    /** AgentScope 执行启动失败。 */
    AGENT_START_FAILED,

    /** 所有者实例中断并导致租约过期。 */
    AGENT_LEASE_EXPIRED
}
