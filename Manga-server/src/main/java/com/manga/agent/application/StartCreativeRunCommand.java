package com.manga.agent.application;

import com.manga.agent.domain.CreativeOperationType;
import com.manga.agent.domain.CreativeResourceReference;
import com.manga.agent.domain.CreativeRunTriggerSource;

import java.util.Objects;

/**
 * 描述一次创作运行启动请求。
 */
public record StartCreativeRunCommand(
        /** 发起运行的用户主键。 */
        long userId,
        /** 请求来自助手会话还是页面上的手动操作。 */
        CreativeRunTriggerSource triggerSource,
        /** 本次运行需要完成的业务操作。 */
        CreativeOperationType operationType,
        /** 可选的既有会话标识，为空时创建新会话。 */
        String conversationId,
        /** 可选的项目主键，用于权限校验和运行时路由。 */
        Long projectId,
        /** 可选的直接操作目标；手动领域操作通常必须提供。 */
        CreativeResourceReference target,
        /** 用户本次提交的自然语言指令或操作补充说明。 */
        String instruction
) {

    public StartCreativeRunCommand {
        Objects.requireNonNull(triggerSource, "创作运行触发来源不能为空");
        Objects.requireNonNull(operationType, "创作操作类型不能为空");
    }

    /** 创建由助手会话消息触发的运行命令。 */
    public static StartCreativeRunCommand conversation(long userId, String conversationId, Long projectId,
            String instruction) {
        return new StartCreativeRunCommand(userId, CreativeRunTriggerSource.CONVERSATION,
                CreativeOperationType.ASSISTANT_CHAT, conversationId, projectId, null, instruction);
    }

    /** 创建由用户页面操作触发的运行命令。 */
    public static StartCreativeRunCommand manual(long userId, Long projectId, CreativeOperationType operationType,
            CreativeResourceReference target, String instruction) {
        return new StartCreativeRunCommand(userId, CreativeRunTriggerSource.MANUAL,
                operationType, null, projectId, Objects.requireNonNull(target, "手动创作目标不能为空"), instruction);
    }
}
