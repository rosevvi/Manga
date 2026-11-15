package com.manga.agent.api;

import com.manga.agent.domain.CreativeRunStatus;
import java.util.List;
/** 在同一事务快照中返回消息页和当前 Run 的投影 cursor。 */
public record CreativeConversationHistory(
        /** 会话基本信息。 */ CreativeConversationView conversation,
        /** 按展示顺序排列的一页消息。 */ List<CreativeMessageView> messages,
        /** 更早一页的主键游标，无更多消息时为空。 */ Long nextBeforeId,
        /** 可选的最近未收敛 Run，包含尚在停止中的取消轮次。 */ ActiveRun activeRun,
        /** 最近运行的投影和事实水位，终态后仍可补齐未投影正文。 */ List<ProjectionWatermark> projections
) {
    /** 消息覆盖水位用于剔除已投影的 SSE 增量，避免历史正文重复展示。 */
    public record ActiveRun(
            /** 活动运行标识。 */ String runId,
            /** 当前运行状态。 */ CreativeRunStatus status,
            /** 当前消息已覆盖的运行内事件序号。 */ long projectedThroughSequence
    ) { }
    /** 消息快照已覆盖范围与事件事实最高水位。 */
    public record ProjectionWatermark(
            /** 所属运行。 */ String runId,
            /** 已投影事件水位。 */ long projectedThroughSequence,
            /** 已提交事实水位。 */ long throughSequence,
            /** 当前运行状态。 */ CreativeRunStatus status
    ) { }
}
