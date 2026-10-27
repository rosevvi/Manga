package com.manga.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.manga.entity.CreativeStep;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 提供 Step 查询及带 owner epoch 围栏的原子状态变更。
 */
public interface CreativeStepMapper extends BaseMapper<CreativeStep> {
    /** 释放执行权并持久化审批等待，旧 owner epoch 立即失效。 */
    int pauseOwned(@Param("stepId") String stepId, @Param("owner") String owner,
            @Param("epoch") long epoch, @Param("now") LocalDateTime now, @Param("actor") String actor);

    /** 审批恢复时替换状态会话，仅推进调度版本，不修改 Blueprint。 */
    int resumeApproval(@Param("stepId") String stepId, @Param("sessionId") String sessionId,
            @Param("result") String result, @Param("maxAttempts") int maxAttempts, @Param("actor") String actor);

    /** 按稳定标识查询 Step。 */
    CreativeStep findByStepId(@Param("stepId") String stepId);

    /** 查询 Run 下的全部 Step。 */
    List<CreativeStep> findByRunId(@Param("runId") String runId);

    /** 查询需要重新投递的 READY Step。 */
    List<CreativeStep> findReady(@Param("limit") int limit);

    /** 查询租约已经过期的运行中 Step。 */
    List<CreativeStep> findExpired(@Param("now") LocalDateTime now, @Param("limit") int limit);

    /** 原子领取指定调度版本的 READY Step，并递增 owner epoch。 */
    int claimReady(@Param("stepId") String stepId,
            @Param("scheduleVersion") long scheduleVersion,
            @Param("ownerInstanceId") String ownerInstanceId,
            @Param("leaseUntil") LocalDateTime leaseUntil,
            @Param("updatedBy") String updatedBy);

    /** 使用 owner epoch 围栏续租运行中的 Step。 */
    int renewLease(@Param("stepId") String stepId,
            @Param("ownerInstanceId") String ownerInstanceId,
            @Param("ownerEpoch") long ownerEpoch,
            @Param("now") LocalDateTime now,
            @Param("leaseUntil") LocalDateTime leaseUntil,
            @Param("updatedBy") String updatedBy);

    /** 使用 owner epoch 围栏原子完成 Step。 */
    int completeOwned(@Param("stepId") String stepId,
            @Param("ownerInstanceId") String ownerInstanceId,
            @Param("ownerEpoch") long ownerEpoch,
            @Param("outputJson") String outputJson,
            @Param("finishedAt") LocalDateTime finishedAt,
            @Param("updatedBy") String updatedBy);

    /** 使用 owner epoch 围栏原子失败 Step。 */
    int failOwned(@Param("stepId") String stepId,
            @Param("ownerInstanceId") String ownerInstanceId,
            @Param("ownerEpoch") long ownerEpoch,
            @Param("errorCode") String errorCode,
            @Param("errorMessage") String errorMessage,
            @Param("finishedAt") LocalDateTime finishedAt,
            @Param("updatedBy") String updatedBy);

    /** 使用 owner epoch 围栏将取消请求确认成终态。 */
    int cancelOwned(@Param("stepId") String stepId,
            @Param("ownerInstanceId") String ownerInstanceId,
            @Param("ownerEpoch") long ownerEpoch,
            @Param("finishedAt") LocalDateTime finishedAt,
            @Param("updatedBy") String updatedBy);

    /** 将尚未执行的 Step 直接取消。 */
    int cancelWaitingByRunId(@Param("runId") String runId,
            @Param("finishedAt") LocalDateTime finishedAt,
            @Param("updatedBy") String updatedBy);

    /** 向运行中的 Step 发出取消请求。 */
    int requestCancelRunningByRunId(@Param("runId") String runId,
            @Param("updatedBy") String updatedBy);

    /** 原子恢复租约过期且未超过重试次数的 Step。 */
    int recoverExpired(@Param("stepId") String stepId,
            @Param("ownerEpoch") long ownerEpoch,
            @Param("now") LocalDateTime now,
            @Param("updatedBy") String updatedBy);

    /** 将重试次数耗尽且租约过期的 Step 原子标记为失败。 */
    int failExpiredExhausted(@Param("stepId") String stepId,
            @Param("ownerEpoch") long ownerEpoch,
            @Param("now") LocalDateTime now,
            @Param("errorCode") String errorCode,
            @Param("errorMessage") String errorMessage,
            @Param("updatedBy") String updatedBy);

    /** 将租约过期的取消请求收敛为取消终态。 */
    int cancelExpired(@Param("stepId") String stepId,
            @Param("ownerEpoch") long ownerEpoch,
            @Param("now") LocalDateTime now,
            @Param("updatedBy") String updatedBy);

    /** 统计 Run 下尚未终止的 Step 数。 */
    int countNonTerminal(@Param("runId") String runId);

    /** 统计 Run 下失败的 Step 数。 */
    int countFailed(@Param("runId") String runId);

    /** 统计 Run 下取消的 Step 数。 */
    int countCancelled(@Param("runId") String runId);
}
