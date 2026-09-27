package com.manga.agent.domain;

import java.util.Arrays;

/**
 * 定义创作 Run 的稳定生命周期状态。
 */
public enum CreativeRunStatus {

    /** Run 已受理，等待调度。 */
    QUEUED(false),

    /** Run 至少存在一个可运行或正在运行的 Step。 */
    ACTIVE(false),

    /** Run 没有可运行 Step，但仍在等待审批、外部作业或子任务。 */
    WAITING(false),

    /** 已收到取消请求，正在等待所有分支收敛。 */
    CANCEL_REQUESTED(false),

    /** 所有必要 Step 已成功或按策略跳过。 */
    COMPLETED(true),

    /** Run 因不可恢复错误终止。 */
    FAILED(true),

    /** Run 的全部活动分支已停止并完成取消。 */
    CANCELLED(true);

    /** 当前状态是否为不可逆终态。 */
    private final boolean terminal;

    CreativeRunStatus(boolean terminal) {
        this.terminal = terminal;
    }

    /** 返回用于数据库和事件协议的稳定机器编码。 */
    public String code() {
        return name();
    }

    /** 判断当前状态是否为不可逆终态。 */
    public boolean isTerminal() {
        return terminal;
    }

    /** 按稳定机器编码解析状态，拒绝未知编码。 */
    public static CreativeRunStatus fromCode(String code) {
        return Arrays.stream(values())
                .filter(status -> status.code().equals(code))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        CreativeRuntimeMessages.UNKNOWN_RUN_STATUS_TEMPLATE.formatted(code)));
    }

}
