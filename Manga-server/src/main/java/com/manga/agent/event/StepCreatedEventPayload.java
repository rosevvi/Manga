package com.manga.agent.event;

import com.manga.agent.domain.CreativeStepType;

/**
 * 描述新建 Step 的稳定事件载荷。
 */
public record StepCreatedEventPayload(
        /** 新建 Step 标识。 */
        String stepId,
        /** Step 执行器路由类型。 */
        CreativeStepType stepType,
        /** Step 初始调度版本。 */
        long scheduleVersion
) {
}
