package com.manga.agent.domain;

import java.util.Arrays;

/**
 * 定义可恢复创作 Step 的稳定生命周期状态。
 */
public enum CreativeStepStatus {

    /** Step 已创建，但依赖尚未满足。 */
    PENDING(false),
    /** Step 已满足依赖，可以被执行器领取。 */
    READY(false),
    /** Step 正由一个带租约和 epoch 的执行器处理。 */
    RUNNING(false),
    /** Step 等待用户审批。 */
    WAITING_APPROVAL(false),
    /** Step 等待图片、视频或语音外部作业。 */
    WAITING_JOB(false),
    /** Step 等待子 Step 收敛。 */
    WAITING_CHILDREN(false),
    /** Step 等待到达下一次重试时间。 */
    WAITING_RETRY(false),
    /** Step 已成功完成。 */
    COMPLETED(true),
    /** Step 已不可恢复地失败。 */
    FAILED(true),
    /** Step 已收到取消请求，正在释放外部资源。 */
    CANCEL_REQUESTED(false),
    /** Step 已完成取消。 */
    CANCELLED(true),
    /** Step 按工作流策略跳过。 */
    SKIPPED(true);

    /** 当前状态是否为不可逆终态。 */
    private final boolean terminal;

    CreativeStepStatus(boolean terminal) {
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
    public static CreativeStepStatus fromCode(String code) {
        return Arrays.stream(values())
                .filter(status -> status.code().equals(code))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        CreativeRuntimeMessages.UNKNOWN_STEP_STATUS_TEMPLATE.formatted(code)));
    }
}
