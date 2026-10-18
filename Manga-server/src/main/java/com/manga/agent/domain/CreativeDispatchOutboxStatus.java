package com.manga.agent.domain;

/**
 * 定义 Step 调度 Outbox 的稳定投递状态。
 */
public enum CreativeDispatchOutboxStatus {

    /** 调度记录等待领取或重试。 */
    PENDING,
    /** 调度记录已被一个实例临时领取。 */
    CLAIMED,
    /** 调度信号已经交给本地执行器。 */
    DISPATCHED
}
