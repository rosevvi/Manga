package com.manga.agent.capability;

import com.fasterxml.jackson.databind.JsonNode;
import com.manga.agent.domain.AgentPermissionPolicy;

/**
 * 固化一个 Agent Capability 的可验证工具契约。
 */
public record CapabilityDescriptor(
        /** 能力的稳定机器名称。 */
        CapabilityName name,
        /** 提供给模型的能力说明。 */
        String description,
        /** 工具输入 JSON Schema。 */
        JsonNode inputSchema,
        /** 规范化输入 Schema 的 SHA-256 指纹。 */
        String schemaSha256,
        /** 能力是否保证不修改业务状态。 */
        boolean readOnly,
        /** 能力实现是否允许不同会话并发调用。 */
        boolean concurrencySafe,
        /** 调用能力要求的最低业务权限。 */
        AgentPermissionPolicy requiredPermission,
        /** 能力实现语义版本。 */
        long implementationVersion
) {
}
