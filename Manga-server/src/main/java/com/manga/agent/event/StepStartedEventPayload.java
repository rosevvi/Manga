package com.manga.agent.event;

/**
 * 描述一次带围栏的 Step 执行开始事件。
 */
public record StepStartedEventPayload(
        /** 开始执行的 Step 标识。 */
        String stepId,
        /** 本次执行权围栏代次。 */
        long ownerEpoch,
        /** 当前累计执行尝试次数。 */
        int attemptNo
) {
}
