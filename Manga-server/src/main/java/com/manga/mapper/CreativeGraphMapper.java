package com.manga.mapper;

import com.manga.entity.CreativeDelegation;
import com.manga.entity.CreativeRunBudget;
import com.manga.entity.CreativeStepDependency;
import com.manga.agent.domain.CreativeStepStatus;
import org.apache.ibatis.annotations.Param;
import java.util.List;

/** 协作图的批量查询和状态更新 SQL，调用方必须先取得 Run 行锁。 */
public interface CreativeGraphMapper {
    /** 只写结构化结果，不覆盖并发心跳续签的 lease 字段。 */
    int saveResult(@Param("stepId") String stepId, @Param("owner") String owner, @Param("epoch") long epoch,
            @Param("result") String result, @Param("actor") String actor);
    /** 只写跨轮次已读引用，避免读取审计回写旧 owner 或租约快照。 */
    int recordReads(@Param("stepId") String stepId, @Param("owner") String owner, @Param("epoch") long epoch,
            @Param("reads") String reads, @Param("actor") String actor);
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
