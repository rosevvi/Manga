package com.manga.agent.api;
import com.manga.agent.domain.CreativeOperationType;
import com.manga.agent.domain.CreativeRunStatus;

import java.time.LocalDateTime;
import java.util.List;
/** 项目手动生成任务分页，活动任务单独返回，避免历史分页隐藏待审批任务。 */
public record CreativeManualRunPage(
        /** 当前页任务，按受理主键倒序。 */ List<Item> items,
        /** 更早任务游标；没有更多数据时为空。 */ Long nextBeforeId,
        /** 最新未结束任务，不受当前分页范围影响。 */ Item activeRun
) {
    /** 手动任务公开摘要，不包含模型配置或内部状态。 */
    public record Item(
            /** 数据库分页主键。 */ Long id,
            /** 稳定运行标识。 */ String runId,
            /** 业务操作类型。 */ CreativeOperationType operationType,
            /** 可选目标主键。 */ Long targetId,
            /** 当前状态。 */ CreativeRunStatus status,
            /** 受理时间。 */ LocalDateTime createdAt,
            /** 所属项目，用于任务中心跨项目定位。 */ Long projectId
    ) { }
}
