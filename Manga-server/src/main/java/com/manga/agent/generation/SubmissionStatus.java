package com.manga.agent.generation;
/** 提交 Outbox 的状态，未知请求禁止自动重新投递。 */
public enum SubmissionStatus {
    /** 尚未登记网络提交。 */ PENDING,
    /** 已登记网络意图，执行权由 Job 的 owner epoch 和 lease 约束。 */ CLAIMED,
    /** 已获明确响应，后续只转存或查询。 */ DONE,
    /** 提交结果不明，需要人工核对。 */ UNKNOWN
}
