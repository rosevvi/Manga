package com.manga.agent.event;

/**
 * 统一维护平台内置事件生产方的稳定标识。
 */
public final class CreativeEventSources {

    /** Creative Runtime 应用服务。 */
    public static final String RUNTIME = "creative/runtime";

    /** AgentScope 模型与工具执行适配层。 */
    public static final String AGENT_SCOPE = "agent/agentscope";

    /** 禁止实例化事件来源常量类。 */
    private CreativeEventSources() {
    }
}
