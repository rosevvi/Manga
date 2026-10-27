package com.manga.repository;

import com.manga.entity.CreativeStep;
import com.manga.mapper.CreativeStepMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 封装 Creative Step 的持久化与围栏更新。
 */
@Repository
@RequiredArgsConstructor
public class CreativeStepRepository {

    private final CreativeStepMapper mapper;
    /** 在 Run 锁内暂停，围栏拒绝已经过期或被取消的执行器。 */
    public boolean pauseOwned(String stepId, String owner, long epoch, LocalDateTime now, String actor) {
        return mapper.pauseOwned(stepId, owner, epoch, now, actor) == 1;
    }

    /** 原子恢复等待步骤，保留原 Blueprint 快照。 */
    public boolean resumeApproval(String stepId, String sessionId, String result, int maxAttempts, String actor) {
        return mapper.resumeApproval(stepId, sessionId, result, maxAttempts, actor) == 1;
    }

    /** 创建 Step 并回填数据库主键。 */
    public CreativeStep create(CreativeStep step) {
        mapper.insert(step);
        return step;
    }

    /** 按稳定标识查询 Step。 */
    public Optional<CreativeStep> findByStepId(String stepId) {
        return Optional.ofNullable(mapper.findByStepId(stepId));
    }

    /** 查询 Run 下全部 Step。 */
    public List<CreativeStep> findByRunId(String runId) {
        return mapper.findByRunId(runId);
    }

    /** 查询需要补投递的 READY Step。 */
    public List<CreativeStep> findReady(int limit) {
        return mapper.findReady(limit);
    }

    /** 查询租约已过期的 Step。 */
    public List<CreativeStep> findExpired(LocalDateTime now, int limit) {
        return mapper.findExpired(now, limit);
    }

    /** 原子领取 READY Step。 */
    public boolean claimReady(String stepId, long scheduleVersion, String owner, LocalDateTime leaseUntil,
            String actor) {
        return mapper.claimReady(stepId, scheduleVersion, owner, leaseUntil, actor) == 1;
    }

    /** 使用围栏条件续租运行中的 Step。 */
    public boolean renewLease(String stepId, String owner, long epoch, LocalDateTime now,
            LocalDateTime leaseUntil, String actor) {
        return mapper.renewLease(stepId, owner, epoch, now, leaseUntil, actor) == 1;
    }

    /** 使用围栏条件完成 Step。 */
    public boolean completeOwned(String stepId, String owner, long epoch, String output,
            LocalDateTime finishedAt, String actor) {
        return mapper.completeOwned(stepId, owner, epoch, output, finishedAt, actor) == 1;
    }

    /** 使用围栏条件失败 Step。 */
    public boolean failOwned(String stepId, String owner, long epoch, String errorCode, String errorMessage,
            LocalDateTime finishedAt, String actor) {
        return mapper.failOwned(stepId, owner, epoch, errorCode, errorMessage, finishedAt, actor) == 1;
    }

    /** 使用围栏条件确认取消 Step。 */
    public boolean cancelOwned(String stepId, String owner, long epoch, LocalDateTime finishedAt, String actor) {
        return mapper.cancelOwned(stepId, owner, epoch, finishedAt, actor) == 1;
    }

    /** 取消尚未被执行器领取的 Step。 */
    public int cancelWaitingByRunId(String runId, LocalDateTime finishedAt, String actor) {
        return mapper.cancelWaitingByRunId(runId, finishedAt, actor);
    }

    /** 向运行中 Step 发出取消请求。 */
    public int requestCancelRunningByRunId(String runId, String actor) {
        return mapper.requestCancelRunningByRunId(runId, actor);
    }

    /** 恢复租约过期的 Step。 */
    public boolean recoverExpired(CreativeStep step, LocalDateTime now, String actor) {
        return mapper.recoverExpired(step.getStepId(), step.getOwnerEpoch(), now, actor) == 1;
    }

    /** 将重试耗尽且租约过期的 Step 置为失败终态。 */
    public boolean failExpiredExhausted(CreativeStep step, LocalDateTime now, String errorCode,
            String errorMessage, String actor) {
        return mapper.failExpiredExhausted(step.getStepId(), step.getOwnerEpoch(), now,
                errorCode, errorMessage, actor) == 1;
    }

    /** 收敛租约过期的取消请求。 */
    public boolean cancelExpired(CreativeStep step, LocalDateTime now, String actor) {
        return mapper.cancelExpired(step.getStepId(), step.getOwnerEpoch(), now, actor) == 1;
    }

    /** 统计尚未终止的 Step。 */
    public int countNonTerminal(String runId) {
        return mapper.countNonTerminal(runId);
    }

    /** 统计失败 Step。 */
    public int countFailed(String runId) {
        return mapper.countFailed(runId);
    }

    /** 统计取消 Step。 */
    public int countCancelled(String runId) {
        return mapper.countCancelled(runId);
    }
}
