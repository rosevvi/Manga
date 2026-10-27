package com.manga.agent.changeset;

/** 审批接口只接受用户主动的批准或拒绝。 */
public enum ApprovalDecision {
    /** 批准不可变提案并尝试应用。 */
    APPROVE,
    /** 拒绝提案并将评论反馈给 Agent。 */
    REJECT
}
