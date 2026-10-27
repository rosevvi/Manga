package com.manga.agent.changeset;

/** 审批聚合的最终决策；批准与业务应用在同一事务提交。 */
public enum ApprovalStatus {
    /** 待用户决定。 */
    PENDING,
    /** 用户批准且应用成功。 */
    APPROVED,
    /** 用户拒绝。 */
    REJECTED,
    /** 用户尝试批准，但版本校验冲突。 */
    CONFLICTED,
    /** 有效期已过。 */
    EXPIRED,
    /** 运行已取消。 */
    CANCELLED
}
