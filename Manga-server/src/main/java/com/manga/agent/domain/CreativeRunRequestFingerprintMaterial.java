package com.manga.agent.domain;

import com.manga.agent.profile.AgentProfileSelection;
import com.fasterxml.jackson.annotation.JsonInclude;

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
        String instruction,
        /** 专业配置、精确测试版本和模型选择参与幂等校验。 */
        @JsonInclude(JsonInclude.Include.NON_NULL) AgentProfileSelection agentSelection
) {
    /** 默认业务操作没有显式选择，保持原请求指纹材料。 */
    public CreativeRunRequestFingerprintMaterial(CreativeRunTriggerSource triggerSource, CreativeOperationType operationType,
            String conversationId, Long projectId, CreativeResourceReference target, AgentPermissionPolicy permissionPolicy, String instruction) {
        this(triggerSource, operationType, conversationId, projectId, target, permissionPolicy, instruction, null);
    }
}
