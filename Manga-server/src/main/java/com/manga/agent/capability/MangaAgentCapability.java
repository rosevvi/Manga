package com.manga.agent.capability;

/**
 * 定义 Manga 业务能力到 Agent 工具的稳定适配边界。
 */
public interface MangaAgentCapability {

    /** 返回用于 Blueprint 校验和 Toolkit 注册的能力描述。 */
    CapabilityDescriptor descriptor();

    /** 在显式运行时上下文中执行能力。 */
    CapabilityResult execute(CapabilityCommand command, AgentRuntimeContext context);
}
