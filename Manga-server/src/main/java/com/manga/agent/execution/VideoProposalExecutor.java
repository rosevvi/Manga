package com.manga.agent.execution;

import com.manga.agent.capability.AgentRuntimeContext;
import com.manga.agent.changeset.ChangeSetApprovalService;
import com.manga.agent.changeset.ChangeSetCodec;
import com.manga.agent.changeset.ChangeSetKind;
import com.manga.agent.changeset.ChangeSetProposalService;
import com.manga.agent.domain.CreativeErrorCode;
import com.manga.agent.domain.CreativeStepInput;
import com.manga.agent.domain.CreativeStepType;
import com.manga.agent.harness.AgentRuntimeException;
import com.manga.agent.generation.GenerationError;
import com.manga.agent.generation.GenerationJobEvent;
import com.manga.agent.generation.GenerationJobStatus;
import com.manga.common.exception.BusinessException;
import com.manga.repository.CreativeStepRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.ZoneId;

/** 用户手动指定视频参数时直接生成报价提案，恢复后按真实作业结果结束。 */
@Component
@RequiredArgsConstructor
public class VideoProposalExecutor implements CreativeStepExecutor {
    private final CreativeStepRepository steps;
    private final CreativeStepLifecycleService lifecycle;
    private final ChangeSetCodec codec;
    private final ChangeSetProposalService proposals;
    private final ChangeSetApprovalService approvals;

    @Override
    public CreativeStepType stepType() { return CreativeStepType.VIDEO_PROPOSAL; }

    /** 复用既有审批及 owner epoch 围栏，全程无需文本模型。 */
    @Override
    public void execute(ClaimedCreativeStep claim) {
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
            var input = codec.read(claim.inputJson(), CreativeStepInput.class);
            var context = new AgentRuntimeContext(claim.userId(), claim.projectId(), null, claim.runId(), claim.stepId(),
                    claim.stateSessionId(), claim.ownerInstanceId(), claim.ownerEpoch(),
                    claim.deadlineAt().atZone(ZoneId.systemDefault()).toInstant(), claim.permissionPolicy());
            try {
                proposals.propose(ChangeSetKind.VIDEO_GENERATION, codec.tree(input.instruction()), context);
            } catch (BusinessException | IllegalStateException exception) {
                throw new AgentRuntimeException(CreativeErrorCode.INVALID_REQUEST, GenerationError.INVALID.message());
            }
        }
        if (!approvals.suspendIfProposed(claim)) lifecycle.acknowledgeCancellation(claim);
    }
}
