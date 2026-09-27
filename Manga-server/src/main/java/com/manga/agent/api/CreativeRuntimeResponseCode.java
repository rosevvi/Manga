package com.manga.agent.api;

import com.manga.common.api.ResponseCode;

/**
 * 定义创作运行时的稳定响应码。
 */
public enum CreativeRuntimeResponseCode implements ResponseCode {

    /** 同一幂等键被用于不同的创作请求。 */
    IDEMPOTENCY_KEY_CONFLICT(
            "IDEMPOTENCY_KEY_CONFLICT",
            "同一 requestId 不能用于不同的创作请求"
    ),

    /** 会话或目标资源已经存在冲突的活跃 Run。 */
    ACTIVE_RUN_CONFLICT(
            "ACTIVE_RUN_CONFLICT",
            "目标已有未结束的创作 Run"
    ),

    /** Creative Run 状态迁移不合法，消息参数依次为当前状态和目标状态。 */
    INVALID_RUN_STATE_TRANSITION(
            "INVALID_RUN_STATE_TRANSITION",
            "创作 Run 不允许从 %s 转换到 %s"
    ),

    /** Creative Step 状态迁移不合法，消息参数依次为当前状态和目标状态。 */
    INVALID_STEP_STATE_TRANSITION(
            "INVALID_STEP_STATE_TRANSITION",
            "创作 Step 不允许从 %s 转换到 %s"
    );

    /** 稳定业务编码。 */
    private final String code;

    /** 默认客户端提示。 */
    private final String message;

    CreativeRuntimeResponseCode(String code, String message) {
        this.code = code;
        this.message = message;
    }

    @Override
    public String code() {
        return code;
    }

    @Override
    public String message() {
        return message;
    }
}
