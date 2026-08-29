package com.manga.common.enums;

/**
 * 定义外部扫码登录会话的生命周期状态。
 */
public enum ExternalLoginStatus {
    WAITING,
    CONFIRMED,
    CONSUMED,
    EXPIRED
}
