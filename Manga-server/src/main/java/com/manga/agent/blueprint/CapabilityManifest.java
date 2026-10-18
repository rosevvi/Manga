package com.manga.agent.blueprint;

import com.manga.agent.capability.CapabilityName;
import com.manga.agent.domain.AgentPermissionPolicy;

/**
 * 固化 Blueprint 对一个 Agent Capability 的精确依赖。
 */
public record CapabilityManifest(
        /** 能力稳定名称。 */ CapabilityName name,
        /** 输入 Schema 的 SHA-256 指纹。 */ String schemaSha256,
        /** 能力是否只读。 */ boolean readOnly,
        /** 能力是否允许并发调用。 */ boolean concurrencySafe,
        /** 能力要求的最低权限。 */ AgentPermissionPolicy requiredPermission,
        /** 能力实现语义版本。 */ long implementationVersion
) {
}
