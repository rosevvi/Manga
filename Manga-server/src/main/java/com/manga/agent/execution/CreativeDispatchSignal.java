package com.manga.agent.execution;

/**
 * 定义一次需要投递的 Step 调度信号。
 */
public record CreativeDispatchSignal(
        /** 待调度 Step 标识。 */
        String stepId,
        /** Step 当前调度版本。 */
        long scheduleVersion
) {
}
