package com.manga.agent.event;

/**
 * 描述成功完成的 Step 及其执行围栏。
 */
public record StepCompletedEventPayload(
        /** 成功完成的 Step 标识。 */
        String stepId,
        /** 完成提交使用的 owner epoch。 */
        long ownerEpoch
) {
}
