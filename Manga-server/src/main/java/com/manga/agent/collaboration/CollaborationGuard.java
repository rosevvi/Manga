package com.manga.agent.collaboration;

import com.manga.agent.domain.CreativeErrorCode;
import com.manga.agent.domain.CreativeRunStatus;
import com.manga.agent.domain.CreativeStepStatus;
import com.manga.agent.capability.AgentRuntimeContext;
import com.manga.agent.harness.AgentRuntimeException;
import com.manga.entity.CreativeRun;
import com.manga.entity.CreativeStep;
import java.time.LocalDateTime;
import java.util.Objects;

/** 所有有副作用协作工具共用的执行围栏。 */
public final class CollaborationGuard {
    /** 校验执行权、租约与独立状态会话。 */
    public static void requireOwner(CreativeStep step, String owner, long epoch, String session) {
        if (Objects.isNull(step) || step.getStatus() != CreativeStepStatus.RUNNING
                || !Objects.equals(step.getOwnerInstanceId(), owner) || step.getOwnerEpoch() != epoch
                || !Objects.equals(step.getStateSessionId(), session) || Objects.isNull(step.getLeaseUntil())
                || !step.getLeaseUntil().isAfter(LocalDateTime.now())) {
            throw new AgentRuntimeException(CreativeErrorCode.OWNER_LEASE_LOST, CollaborationMessages.OWNER_LOST);
        }
    }
    /** 校验可信身份、Run 边界及截止时间。 */
    public static void requireRun(CreativeRun run, CreativeStep step, AgentRuntimeContext context) {
        if (Objects.isNull(run) || run.getStatus() != CreativeRunStatus.ACTIVE
                || !Objects.equals(run.getUserId(), context.userId())
                || !Objects.equals(run.getProjectId(), context.projectId())
                || Objects.isNull(step) || !Objects.equals(step.getRunId(), run.getRunId())
                || !run.getDeadlineAt().isAfter(LocalDateTime.now())) {
            throw new AgentRuntimeException(CreativeErrorCode.OWNER_LEASE_LOST, CollaborationMessages.OWNER_LOST);
        }
        requireOwner(step, context.ownerInstanceId(), context.ownerEpoch(), context.stateSessionId());
    }
    private CollaborationGuard() { }
}
