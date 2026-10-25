package com.manga.agent.domain;

/**
 * 定义创作事件中公开的工具调用状态，避免前端依赖 AgentScope 内部枚举。
 */
public enum CreativeToolStatus {

    /** 工具调用已经开始。 */
    RUNNING,
    /** 工具调用成功结束。 */
    SUCCESS,
    /** 工具调用执行失败。 */
    ERROR,
    /** 工具调用被运行时中断。 */
    INTERRUPTED,
    /** 工具调用因权限或策略被拒绝。 */
    DENIED
}
