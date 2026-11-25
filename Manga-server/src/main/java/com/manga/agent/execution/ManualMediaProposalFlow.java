package com.manga.agent.execution;

import com.fasterxml.jackson.databind.JsonNode;
import com.manga.agent.capability.AgentRuntimeContext;
import com.manga.agent.changeset.ChangeSetApprovalService;
import com.manga.agent.changeset.ChangeSetCodec;
import com.manga.agent.changeset.ChangeSetKind;
import com.manga.agent.changeset.ChangeSetProposalService;
import com.manga.agent.domain.CreativeErrorCode;
import com.manga.agent.harness.AgentRuntimeException;
import com.manga.agent.generation.GenerationError;
import com.manga.agent.generation.GenerationJobEvent;
import com.manga.agent.generation.GenerationJobStatus;
import com.manga.common.exception.BusinessException;
import com.manga.repository.CreativeStepRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.ZoneId;
import java.util.function.Supplier;

/** 共用页面媒体提案的审批和生成恢复流程，不占用文本模型。 */
@Component
@RequiredArgsConstructor
public class ManualMediaProposalFlow {
    private final CreativeStepRepository steps;
    private final CreativeStepLifecycleService lifecycle;
    private final ChangeSetCodec codec;
    private final ChangeSetProposalService proposals;
    private final ChangeSetApprovalService approvals;

    /** 复用既有审批及 owner epoch 围栏，全程无需文本模型。 */
    public void execute(ClaimedCreativeStep claim, ChangeSetKind kind, Supplier<JsonNode> payload) {
        if (!lifecycle.canContinue(claim)) {
            lifecycle.acknowledgeCancellation(claim);
            return;
        }
        var step = steps.findByStepId(claim.stepId()).orElseThrow();
        if (step.getGenerationResultJson() != null) {
            var result = codec.read(step.getGenerationResultJson(), GenerationJobEvent.class);
            if (result.status() != GenerationJobStatus.SUCCEEDED) {
                throw new AgentRuntimeException(CreativeErrorCode.CAPABILITY_EXECUTION_FAILED,
                        result.errorMessage() == null ? GenerationError.FAILED.message() : result.errorMessage());
            }
            lifecycle.complete(claim, step.getGenerationResultJson());
            return;
        }
        if (step.getApprovalResultJson() != null) {
            lifecycle.complete(claim, step.getApprovalResultJson());
            return;
        }
        if (!approvals.hasDraft(claim.stepId())) {
            var context = new AgentRuntimeContext(claim.userId(), claim.projectId(), null, claim.runId(), claim.stepId(),
                    claim.stateSessionId(), claim.ownerInstanceId(), claim.ownerEpoch(),
                    claim.deadlineAt().atZone(ZoneId.systemDefault()).toInstant(), claim.permissionPolicy());
            try {
                proposals.propose(kind, payload.get(), context);
            } catch (BusinessException | IllegalStateException exception) {
                throw new AgentRuntimeException(CreativeErrorCode.INVALID_REQUEST, GenerationError.INVALID.message());
            }
        }
        if (!approvals.suspendIfProposed(claim)) lifecycle.acknowledgeCancellation(claim);
    }
}
