package com.manga.agent.collaboration;

/** 协作校验和运行失败的脱敏说明。 */
public final class CollaborationMessages {
    /** 计划结构、角色或资源边界非法。 */
    public static final String INVALID_PLAN = "协作计划无效：节点、依赖、角色或项目范围不满足约束";
    /** 执行围栏校验失败。 */
    public static final String OWNER_LOST = "当前协作步骤的执行权已失效";
    /** 幂等键被用于不同内容。 */
    public static final String CHECKPOINT_CONFLICT = "同一执行检查点只能提交一个不可变计划或结果";
    /** 冻结预算拒绝继续执行。 */
    public static final String BUDGET_EXCEEDED = "协作预算已耗尽，无法继续创建步骤或调用模型";
    /** Worker 缺少合法结构化输出。 */
    public static final String RESULT_REQUIRED = "Worker 必须通过 submit_creative_step_result 提交合法的结构化结果";
    /** 最终候选尚未满足评审门槛。 */
    public static final String REVIEW_REQUIRED = "协作计划必须经连续性评审通过后才能提交最终提案";
    /** 依赖失败的传播说明。 */
    public static final String DEPENDENCY_FAILED = "协作依赖失败，已停止其余步骤";
    /** 跨依赖或跨运行结果访问被拒绝。 */
    public static final String RESULT_SCOPE_DENIED = "只允许读取本次 Run 中已完成且属于授权依赖范围的结果";
    /** 恢复调用时注入的有界协作上下文模板。 */
    public static final String CONTEXT_TEMPLATE = "\n\n【持久化协作上下文】\n%s\n只使用摘要和结果引用；需要候选草稿时调用 read_delegated_result。不得读取其他 Agent 的内部状态。";
    /** 内置 Worker 提示词模板，角色与结果类型由平台指定。 */
    public static final String WORKER_TEMPLATE = "你是漫剧平台的%s。只负责被委派的任务，不直接保存业务数据，不再委派其他 Agent。读取上游结果后，必须调用 submit_creative_step_result 提交 %s 类型结果。候选草稿必须符合对应 ChangeSet schema。评审必须读取候选内容，提交 PASS 或 NEEDS_REPAIR 及具体问题。工具成功提交后立即结束。";
    private CollaborationMessages() { }
}
