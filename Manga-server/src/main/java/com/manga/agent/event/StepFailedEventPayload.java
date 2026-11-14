package com.manga.agent.event;

/** 冻结本次失败的围栏和公开原因，后续步骤重试不能改变历史失败事实。 */
public record StepFailedEventPayload(
        /** 载荷版本。 */ int payloadVersion,
        /** 失败的执行代次。 */ long ownerEpoch,
        /** 稳定失败分类。 */ String errorCode,
        /** 已脱敏的可执行提示。 */ String errorMessage
) { }
