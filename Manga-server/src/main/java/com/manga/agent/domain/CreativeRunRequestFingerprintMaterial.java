package com.manga.agent.domain;

/**
 * 定义幂等请求指纹参与规范化序列化的稳定字段。
 */
public record CreativeRunRequestFingerprintMaterial(
        /** 请求触发来源。 */
        CreativeRunTriggerSource triggerSource,
        /** 请求执行的业务操作。 */
        CreativeOperationType operationType,
        /** 客户端提供的既有会话标识。 */
        String conversationId,
        /** 客户端指定的项目主键。 */
        Long projectId,
        /** 客户端指定的直接目标资源。 */
        CreativeResourceReference target,
        /** 请求允许的最高权限策略。 */
        AgentPermissionPolicy permissionPolicy,
        /** 用户提交的原始创作指令。 */
        String instruction
) {
}
