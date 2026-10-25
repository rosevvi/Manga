package com.manga.agent.domain;

/**
 * 定义可由执行器注册表路由的稳定 Step 类型。
 */
public enum CreativeStepType {

    /** Run 的根工作流；阶段一由 Fake 执行器完成端到端验证。 */
    ROOT_WORKFLOW,

    /** 使用已冻结 Blueprint 执行一次受控 AgentScope 调用。 */
    AGENT_INVOCATION
}
