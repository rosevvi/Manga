package com.manga.agent.generation;
/** 回调只触发事实查询，Inbox 不直接决定生成终态。 */
public enum CallbackInboxStatus {
    /** 已接收，等待供应商作业标识绑定或查询唤醒。 */ PENDING,
    /** 已可靠唤醒 Job，后续由 Job lease 负责。 */ PROCESSED,
    /** 已过期、已终态或已由新事实覆盖。 */ IGNORED
}
