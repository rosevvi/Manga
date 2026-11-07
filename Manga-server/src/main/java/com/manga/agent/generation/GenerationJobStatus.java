package com.manga.agent.generation;
/** 外部生成作业生命周期；UNKNOWN 保留对账责任，不可自动再次扣费提交。 */
public enum GenerationJobStatus {
    /** 待提交。 */ PENDING_SUBMISSION,
    /** 已登记提交意图，网络结果可能不明确。 */ SUBMITTING,
    /** 供应商已受理。 */ SUBMITTED,
    /** 供应商执行中。 */ RUNNING,
    /** 正在转存已生成的媒体。 */ MATERIALIZING,
    /** 结果已转存且可访问。 */ SUCCEEDED,
    /** 明确失败。 */ FAILED,
    /** 已取消，禁止回调复活。 */ CANCELLED,
    /** 提交或执行结果待人工对账。 */ UNKNOWN;
    /** 判断业务终态；UNKNOWN 必须继续占用风险容量直到对账。 */
    public boolean isTerminal() { return this == SUCCEEDED || this == FAILED || this == CANCELLED; }
}
