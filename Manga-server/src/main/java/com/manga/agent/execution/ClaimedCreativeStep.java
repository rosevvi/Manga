package com.manga.agent.execution;

import com.manga.agent.domain.CreativeStepType;

/**
 * 表示一次带 owner epoch 围栏的 Step 执行权快照。
 */
public record ClaimedCreativeStep(
        /** 被领取的 Step 标识。 */
        String stepId,
        /** Step 所属 Run 标识。 */
        String runId,
        /** 执行器路由使用的 Step 类型。 */
        CreativeStepType stepType,
        /** 本次执行权所属实例标识。 */
        String ownerInstanceId,
        /** 本次执行权围栏代次。 */
        long ownerEpoch,
        /** 固化的 Step 输入 JSON。 */
        String inputJson
) {
}
