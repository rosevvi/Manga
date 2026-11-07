package com.manga.agent.collaboration;

import com.manga.agent.domain.CreativeRunStatus;
import com.manga.agent.domain.CreativeStepStatus;
import com.manga.agent.profile.AgentProfileKey;
import java.util.List;

/** 页面从数据库恢复的协作图，不包含内部状态、提示词和模型配置。 */
public record CreativeGraphView(
        /** 所属运行。 */ String runId,
        /** 当前聚合状态。 */ CreativeRunStatus status,
        /** 最新可见事件游标。 */ long throughSequence,
        /** 本 Run 的计划历史。 */ List<Plan> plans,
        /** 父子树及依赖 DAG 节点。 */ List<Node> nodes,
        /** 冻结预算及累计使用量。 */ Budget budget
) {
    /** 委派计划的用户视图。 */
    public record Plan(
            /** 稳定计划标识。 */ String planId,
            /** 委派方步骤。 */ String parentStepId,
            /** 用户可理解的计划名称。 */ String title,
            /** 首次为一的计划轮数。 */ int round,
            /** 持久化状态。 */ DelegationStatus status,
            /** 汇合方式。 */ JoinPolicy joinPolicy,
            /** 失败处理方式。 */ StepFailurePolicy failurePolicy
    ) { }
    /** 各步骤状态可单独定位，内部工具文本不会混入对话消息。 */
    public record Node(
            /** 稳定步骤标识。 */ String stepId,
            /** 可选父步骤。 */ String parentStepId,
            /** 可选计划标识。 */ String planId,
            /** 计划内节点名。 */ String nodeKey,
            /** 执行角色，汇合节点为空。 */ AgentProfileKey profile,
            /** 冻结 Profile 版本，汇合节点为空。 */ Long profileVersion,
            /** 委派树深度。 */ int depth,
            /** 当前步骤状态。 */ CreativeStepStatus status,
            /** 用户可理解的等待原因。 */ String waitingReason,
            /** 必须先完成的步骤。 */ List<String> dependsOn,
            /** 执行代次。 */ long ownerEpoch,
            /** 累计领取次数。 */ int attemptNo,
            /** 有界业务结果摘要。 */ String resultSummary,
            /** 连续性评审结论。 */ ReviewVerdict verdict,
            /** 评审列出的问题。 */ List<String> issues,
            /** 可追溯的产物主键。 */ List<Long> artifactIds,
            /** 稳定失败码。 */ String errorCode,
            /** 脱敏失败摘要。 */ String errorMessage
    ) { }
    /** 预算为最大轮数预留；不能理解为真实 token 或费用统计。 */
    public record Budget(
            /** Step 数上限。 */ int maxSteps,
            /** 深度上限。 */ int maxDepth,
            /** 同时执行上限。 */ int maxParallel,
            /** 修订轮次上限。 */ int maxRepairRounds,
            /** 模型执行次数上限。 */ int maxInvocations,
            /** 最大轮数预留总额。 */ int maxReservedTurns,
            /** 已使用模型执行次数。 */ int usedInvocations,
            /** 已预留最大轮数。 */ int reservedTurns
    ) { }
}
