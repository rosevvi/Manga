package com.manga.agent.profile;

/**
 * 提供可版本化的 Agent Profile 定义。
 */
public interface AgentProfileSource {

    /** 返回指定内置 Profile，不存在时立即失败。 */
    AgentProfile require(AgentProfileKey key);
}
