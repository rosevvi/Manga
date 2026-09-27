package com.manga.agent.domain;

import java.util.Arrays;

/**
 * 定义创作运行可持久化、可检索的稳定失败编码。
 */
public enum CreativeErrorCode {

    /** 请求不满足创作运行契约。 */
    INVALID_REQUEST,
    /** 用户无权访问目标项目或资源。 */
    PERMISSION_DENIED,
    /** 同一幂等请求已经存在。 */
    DUPLICATE_REQUEST,
    /** 目标资源已有冲突的活跃 Run。 */
    ACTIVE_RUN_CONFLICT,
    /** 指定运行时尚未启用。 */
    RUNTIME_UNAVAILABLE,
    /** 模型调用失败。 */
    MODEL_INVOCATION_FAILED,
    /** 工具或能力执行失败。 */
    CAPABILITY_EXECUTION_FAILED,
    /** 执行器租约丢失。 */
    OWNER_LEASE_LOST,
    /** Run 或 Step 超过执行期限。 */
    DEADLINE_EXCEEDED,
    /** 状态转换不符合领域状态机。 */
    INVALID_STATE_TRANSITION,
    /** 用户取消了运行。 */
    CANCELLED_BY_USER,
    /** 未归类的内部执行错误。 */
    INTERNAL_ERROR;

    /** 返回用于数据库和 API 的稳定机器编码。 */
    public String code() {
        return name();
    }

    /** 按稳定机器编码解析错误类型，拒绝未知编码。 */
    public static CreativeErrorCode fromCode(String code) {
        return Arrays.stream(values())
                .filter(error -> error.code().equals(code))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        CreativeRuntimeMessages.UNKNOWN_ERROR_CODE_TEMPLATE.formatted(code)));
    }
}
