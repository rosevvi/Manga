package com.manga.agent.capability;

import com.manga.agent.domain.AgentPermissionPolicy;

import java.time.Instant;

/**
 * 描述一次 Agent 调用的请求级身份、围栏和权限上下文。
 */
public record AgentRuntimeContext(
        /** 当前认证用户主键。 */
        long userId,
        /** 当前项目主键；未绑定项目的会话允许为空。 */
        Long projectId,
        /** 当前创作会话标识。 */
        String conversationId,
        /** 当前创作 Run 标识。 */
        String runId,
        /** 当前创作 Step 标识。 */
        String stepId,
        /** Agent 状态隔离会话标识。 */
        String stateSessionId,
        /** 当前 Step owner 实例标识。 */
        String ownerInstanceId,
        /** 当前 Step owner 围栏代次。 */
        long ownerEpoch,
        /** 当前运行允许执行的最晚时刻。 */
        Instant deadline,
        /** 本次运行允许使用的最高权限。 */
        AgentPermissionPolicy permissionPolicy
) {
}
