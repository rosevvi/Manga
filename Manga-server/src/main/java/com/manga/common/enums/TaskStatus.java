package com.manga.common.enums;

/** 定义统一任务中心的生命周期状态。 */
public enum TaskStatus {
    QUEUED,
    RUNNING,
    COMPLETED,
    PARTIAL_FAILED,
    FAILED,
    CANCELLED
}
