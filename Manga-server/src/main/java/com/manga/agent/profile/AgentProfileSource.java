package com.manga.agent.profile;

/**
 * 提供可版本化的 Agent Profile 定义。
 */
public interface AgentProfileSource {

    /** 返回指定内置 Profile，不存在时立即失败。 */
    AgentProfile require(AgentProfileKey key);

    /** 按数据库标识读取启用的发布版本，内置资源源只识别内置标识。 */
    default AgentProfile require(String profileId) { return require(AgentProfileKey.fromCode(profileId)); }
}
