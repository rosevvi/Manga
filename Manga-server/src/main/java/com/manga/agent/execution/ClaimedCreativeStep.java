package com.manga.agent.execution;

import com.manga.agent.domain.CreativeStepType;
import com.manga.agent.domain.AgentPermissionPolicy;

import java.time.LocalDateTime;

/**
 * 表示一次带 owner epoch 围栏的 Step 执行权快照。
 */
public record ClaimedCreativeStep(
        /** 被领取的 Step 标识。 */
        String stepId,
        /** Step 所属 Run 标识。 */
        String runId,
        /** 执行器路由使用的 Step 类型。 */
        CreativeStepType stepType,
        /** 本次执行权所属实例标识。 */
        String ownerInstanceId,
        /** 本次执行权围栏代次。 */
        long ownerEpoch,
        /** 固化的 Step 输入 JSON。 */
        String inputJson,
        /** 发起 Run 的认证用户主键。 */
        long userId,
        /** Run 绑定的项目主键。 */
        Long projectId,
        /** 可选的创作会话标识。 */
        String conversationId,
        /** Run 允许执行的最晚时间。 */
        LocalDateTime deadlineAt,
        /** Run 冻结的最高 Agent 权限。 */
        AgentPermissionPolicy permissionPolicy,
        /** 创建 Step 时冻结的 Blueprint JSON。 */
        String blueprintSnapshotJson,
        /** Blueprint 快照的 SHA-256 指纹。 */
        String blueprintFingerprint,
        /** AgentScope 状态隔离会话标识。 */
        String stateSessionId
) {
}
