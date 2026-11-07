package com.manga.mapper;

import com.manga.entity.CreativeDelegation;
import com.manga.entity.CreativeRunBudget;
import com.manga.entity.CreativeStepDependency;
import com.manga.agent.domain.CreativeStepStatus;
import org.apache.ibatis.annotations.Param;
import java.util.List;

/** 协作图的批量查询和状态更新 SQL，调用方必须先取得 Run 行锁。 */
public interface CreativeGraphMapper {
    /** 批量读取计划，避免逐步骤查询。 */
    List<CreativeDelegation> plans(@Param("runId") String runId);
    /** 批量读取依赖边。 */
    List<CreativeStepDependency> dependencies(@Param("runId") String runId);
    /** 查询冻结预算。 */
    CreativeRunBudget budget(@Param("runId") String runId);
    /** 有界扫描尚未收敛的计划。 */
    List<String> pendingRuns(@Param("limit") int limit);
    /** 撤销旧执行权并推进等待、恢复或汇合状态。 */
    int transition(@Param("stepId") String stepId, @Param("from") CreativeStepStatus from,
            @Param("to") CreativeStepStatus to, @Param("sessionId") String sessionId,
            @Param("continuation") String continuation, @Param("output") String output,
            @Param("errorCode") String errorCode, @Param("errorMessage") String errorMessage,
            @Param("retryAttempts") int retryAttempts, @Param("actor") String actor);
    /** 单独撤销当前步骤的状态许可，不影响并行兄弟节点。 */
    int retireSession(@Param("sessionId") String sessionId, @Param("actor") String actor);
    /** 执行租约过期恢复时换代，拒绝旧进程晚到的 StateStore 写入。 */
    int replaceSession(@Param("stepId") String stepId, @Param("sessionId") String sessionId,
            @Param("actor") String actor);
}
