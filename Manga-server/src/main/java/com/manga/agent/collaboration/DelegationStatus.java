package com.manga.agent.collaboration;

/** 委派检查点状态，草稿在 Harness 关闭后才物化为可执行子图。 */
public enum DelegationStatus {
    /** 工具已提交，尚未暂停父步骤。 */ DRAFT,
    /** 子图已创建且父步骤正在等待。 */ MATERIALIZED,
    /** 所有依赖成功，父步骤已经恢复。 */ COMPLETED,
    /** 任一子步骤失败，整个 Run 停止继续创作。 */ FAILED,
    /** 用户取消整个 Run。 */ CANCELLED
}
