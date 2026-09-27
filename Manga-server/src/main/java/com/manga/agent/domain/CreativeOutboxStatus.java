package com.manga.agent.domain;

/**
 * 定义创作运行 Outbox 记录的稳定投递状态。
 */
public enum CreativeOutboxStatus {

    /** 记录等待领取或重试。 */
    PENDING,

    /** 记录已经成功投递。 */
    PUBLISHED
}
