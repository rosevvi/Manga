package com.manga.agent.collaboration;

/** 用户修订确认单的持久化生命周期。 */
public enum RepairDecisionStatus {
    /** 等待用户确认，不持有执行资源。 */ PENDING,
    /** 用户已授权创建下一轮修订。 */ CONFIRMED,
    /** 用户结束任务或任务被取消。 */ STOPPED,
    /** 超过确认保留期限。 */ EXPIRED,
    /** 候选生成后的业务版本已变化。 */ CONFLICTED
}
