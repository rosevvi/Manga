package com.manga.agent.api;

import com.manga.agent.domain.AgentPermissionPolicy;
import com.manga.agent.domain.CreativeOperationType;
import com.manga.agent.domain.CreativeResourceType;
import com.manga.agent.domain.CreativeRunStatus;
import com.manga.agent.domain.CreativeRunTriggerSource;

import java.time.LocalDateTime;

/**
 * 返回 Creative Run 的稳定状态快照。
 */
public record CreativeRunResponse(
        /** Run 唯一标识。 */
        String runId,
        /** 启动幂等标识。 */
        String requestId,
        /** 可选的所属助手会话标识。 */
        String conversationId,
        /** 可选的绑定项目主键。 */
        Long projectId,
        /** Run 触发来源。 */
        CreativeRunTriggerSource triggerSource,
        /** Run 执行的业务操作类型。 */
        CreativeOperationType operationType,
        /** 可选的目标资源类型。 */
        CreativeResourceType targetType,
        /** 可选的目标资源主键。 */
        Long targetId,
        /** Run 当前状态。 */
        CreativeRunStatus status,
        /** Run 固化的权限策略。 */
        AgentPermissionPolicy permissionPolicy,
        /** Run 固化的执行引擎版本。 */
        String engineVersion,
        /** Run 固化的工作流版本。 */
        String workflowVersion,
        /** Run 实际开始执行的时间。 */
        LocalDateTime startedAt,
        /** Run 进入终态的时间。 */
        LocalDateTime finishedAt,
        /** 终态错误机器编码。 */
        String errorCode,
        /** 脱敏后的终态错误摘要。 */
        String errorMessage
) {
}
