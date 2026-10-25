package com.manga.agent.api;

import com.manga.agent.domain.CreativeRunStatus;
import java.util.List;
/** 在同一事务快照中返回消息页和当前 Run 的投影 cursor。 */
public record CreativeConversationHistory(
        /** 会话基本信息。 */ CreativeConversationView conversation,
        /** 按展示顺序排列的一页消息。 */ List<CreativeMessageView> messages,
        /** 更早一页的主键游标，无更多消息时为空。 */ Long nextBeforeId,
        /** 可选的最近未收敛 Run，包含尚在停止中的取消轮次。 */ ActiveRun activeRun
) {
    /** SSE 从消息已覆盖的水位继续，正文始终以投影为准。 */
    public record ActiveRun(
            /** 活动运行标识。 */ String runId,
            /** 当前运行状态。 */ CreativeRunStatus status,
            /** 当前消息已覆盖的运行内事件序号。 */ long projectedThroughSequence
    ) { }
}
