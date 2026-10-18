package com.manga.agent.blueprint;

/**
 * 标识可安全共享稳定模型与工具资源的一类 Agent Kernel。
 */
public record AgentKernelKey(
        /** 由 Profile、模型、Prompt、能力和权限共同计算的 SHA-256 值。 */
        String value
) {
}
