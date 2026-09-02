package com.manga.common.enums;

/**
 * 定义外部扫码登录会话的生命周期状态。
 */
public enum ExternalLoginStatus {
    /** 等待用户扫码确认。 */
    WAITING,
    /** 扫码身份已经确认。 */
    CONFIRMED,
    /** 登录结果已经消费。 */
    CONSUMED,
    /** 登录会话已经过期。 */
    EXPIRED
}
