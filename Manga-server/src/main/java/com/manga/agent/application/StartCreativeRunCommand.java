package com.manga.agent.application;

import com.manga.agent.domain.AgentPermissionPolicy;
import com.manga.agent.domain.CreativeOperationType;
import com.manga.agent.domain.CreativeResourceReference;
import com.manga.agent.domain.CreativeRuntimeMessages;
import com.manga.agent.domain.CreativeRunTriggerSource;

import java.util.Objects;

import org.springframework.util.StringUtils;

/**
 * 描述一次创作运行启动请求。
 */
public record StartCreativeRunCommand(
        /** 发起运行的用户主键。 */
        long userId,
        /** 用户侧或调用客户端生成的启动幂等标识。 */
        String requestId,
        /** 请求来自助手会话还是页面上的手动操作。 */
        CreativeRunTriggerSource triggerSource,
        /** 本次运行需要完成的业务操作。 */
        CreativeOperationType operationType,
        /** 可选的既有会话标识，为空时创建新会话。 */
        String conversationId,
        /** 可选的项目主键，用于权限校验和运行上下文绑定。 */
        Long projectId,
        /** 可选的直接操作目标；手动领域操作通常必须提供。 */
        CreativeResourceReference target,
        /** 本次 Run 允许 Agent 使用的最高业务权限。 */
        AgentPermissionPolicy permissionPolicy,
        /** 用户本次提交的自然语言指令或操作补充说明。 */
        String instruction
) {

    public StartCreativeRunCommand {
        if (userId <= 0) {
            throw new IllegalArgumentException(CreativeRuntimeMessages.USER_ID_MUST_BE_POSITIVE);
        }
        if (!StringUtils.hasText(requestId)) {
            throw new IllegalArgumentException(CreativeRuntimeMessages.REQUEST_ID_REQUIRED);
        }
        Objects.requireNonNull(triggerSource, CreativeRuntimeMessages.TRIGGER_SOURCE_REQUIRED);
        Objects.requireNonNull(operationType, CreativeRuntimeMessages.OPERATION_TYPE_REQUIRED);
        Objects.requireNonNull(permissionPolicy, CreativeRuntimeMessages.PERMISSION_POLICY_REQUIRED);
        if (!StringUtils.hasText(instruction)) {
            throw new IllegalArgumentException(CreativeRuntimeMessages.INSTRUCTION_REQUIRED);
        }
        if (Objects.nonNull(projectId) && projectId <= 0) {
            throw new IllegalArgumentException(CreativeRuntimeMessages.PROJECT_ID_MUST_BE_POSITIVE);
        }
        validateScope(triggerSource, operationType, conversationId, projectId, target);
    }

    /** 创建由助手会话消息触发的运行命令。 */
    public static StartCreativeRunCommand conversation(long userId, String requestId, String conversationId,
            Long projectId, String instruction) {
        return new StartCreativeRunCommand(userId, requestId, CreativeRunTriggerSource.CONVERSATION,
                CreativeOperationType.ASSISTANT_CHAT, conversationId, projectId, null,
                AgentPermissionPolicy.READ_ONLY, instruction);
    }

    /** 创建由用户页面操作触发的运行命令。 */
    public static StartCreativeRunCommand manual(long userId, String requestId, Long projectId,
            CreativeOperationType operationType, CreativeResourceReference target,
            AgentPermissionPolicy permissionPolicy, String instruction) {
        return new StartCreativeRunCommand(userId, requestId, CreativeRunTriggerSource.MANUAL,
                operationType, null, projectId, target, permissionPolicy, instruction);
    }

    /** 校验会话和手动入口之间不可混用的业务字段。 */
    private static void validateScope(CreativeRunTriggerSource triggerSource, CreativeOperationType operationType,
            String conversationId, Long projectId, CreativeResourceReference target) {
        if (triggerSource == CreativeRunTriggerSource.CONVERSATION) {
            if (operationType != CreativeOperationType.ASSISTANT_CHAT) {
                throw new IllegalArgumentException(CreativeRuntimeMessages.CONVERSATION_OPERATION_INVALID);
            }
            if (Objects.nonNull(target)) {
                throw new IllegalArgumentException(CreativeRuntimeMessages.CONVERSATION_CANNOT_HAVE_TARGET);
            }
            return;
        }
        if (StringUtils.hasText(conversationId)) {
            throw new IllegalArgumentException(CreativeRuntimeMessages.MANUAL_CONVERSATION_NOT_ALLOWED);
        }
        if (Objects.isNull(projectId)) {
            throw new IllegalArgumentException(CreativeRuntimeMessages.MANUAL_PROJECT_REQUIRED);
        }
        if (operationType == CreativeOperationType.ASSISTANT_CHAT) {
            throw new IllegalArgumentException(CreativeRuntimeMessages.MANUAL_CHAT_NOT_ALLOWED);
        }
        Objects.requireNonNull(target, CreativeRuntimeMessages.TARGET_REQUIRED_FOR_MANUAL);
    }
}
