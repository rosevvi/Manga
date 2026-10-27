package com.manga.agent.changeset;

/** 提案从生成检查点到不可逆业务结果的生命周期。 */
public enum ChangeSetStatus {
    /** 工具已保存提案，执行器尚未释放资源并开放审批。 */
    DRAFT,
    /** 已持久化暂停，等待用户决定。 */
    PENDING,
    /** 已通过领域服务原子应用。 */
    APPLIED,
    /** 用户拒绝，业务数据未改变。 */
    REJECTED,
    /** 基础版本已发生变化，必须重新生成。 */
    CONFLICTED,
    /** 超过审批有效期。 */
    EXPIRED,
    /** 所属运行被取消或终止。 */
    CANCELLED
}
