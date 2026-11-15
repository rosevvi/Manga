package com.manga.agent.collaboration;

/** 修订检查点、幂等和上下文校验使用的公开说明。 */
public final class RepairMessages {
    /** 不允许确认其他用户或其他任务的检查点。 */ public static final String UNAVAILABLE = "修订确认单不存在或无权访问";
    /** 已决定的检查点不接受不同命令。 */ public static final String DECIDED = "该修订确认已处理，请刷新任务";
    /** 项目变化后不能沿用旧候选。 */ public static final String CONFLICT = "项目或章节已变化，请基于最新内容重新生成";
    /** 等待到期结束任务。 */ public static final String EXPIRED = "修订确认已过期，候选草稿已保留";
    /** 继续修订超过冻结预算。 */ public static final String EXHAUSTED = "修订轮次已达到本次任务上限，候选与评审意见已保留";
    /** 修订只能基于已完成的合法候选及评审。 */ public static final String INVALID_SOURCE = "修订缺少已完成的候选草稿或评审结果";
    /** 显式要求读取上一轮结果，不将未保存草稿误认为正式分镜。 */
    public static final String REPAIR_INSTRUCTION = "修订上一轮候选，不重新从零生成。必须先调用 read_delegated_result 读取候选步骤 %s 和评审步骤 %s，按问题逐项修复后提交新的完整候选。正式业务列表不包含未批准草稿，不能把其为空当作没有候选。用户补充意见：%s";
    /** 评审仅针对新修订候选。 */ public static final String REVIEW_INSTRUCTION = "读取前置修订步骤的候选，并读取原评审步骤 %s；逐项复核旧问题及候选与章节的一致性，提交 PASS 或 NEEDS_REPAIR。不得提交业务变更。";
    /** 稳定的修订节点标识。 */ public static final String REPAIR_NODE = "repair";
    /** 稳定的评审节点标识。 */ public static final String REVIEW_NODE = "review";
    private RepairMessages() { }
}
