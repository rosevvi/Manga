package com.manga.agent.changeset;

import com.manga.agent.domain.SystemActor;
import com.manga.entity.CreativeChangeSet;
import com.manga.repository.CreativeApprovalRepository;
import com.manga.repository.CreativeChangeSetRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/** 和运行取消共用事务及行锁，避免停止后仍显示可批准提案。 */
@Component
@RequiredArgsConstructor
public class ChangeSetInvalidationListener {
    private final CreativeChangeSetRepository proposals;
    private final CreativeApprovalRepository approvals;
    private final ChangeSetCodec codec;

    /** 只撤销草稿和未决审批，已经应用的业务结果保持可审计。 */
    @EventListener
    @Transactional(propagation = Propagation.MANDATORY)
    public void invalidate(CreativeRunStopped stopped) {
        String actor = SystemActor.CREATIVE_RUNTIME.code();
        for (CreativeChangeSet proposal : proposals.findByRun(stopped.runId())) {
            if (proposal.getStatus() != ChangeSetStatus.DRAFT && proposal.getStatus() != ChangeSetStatus.PENDING) continue;
            ChangeSetStateMachine.requireTransition(proposal.getStatus(), ChangeSetStatus.CANCELLED);
            proposal.setStatus(ChangeSetStatus.CANCELLED);
            proposal.setResultJson(codec.write(new ApprovalResult(proposal.getChangeSetId(), ApprovalStatus.CANCELLED,
                    ChangeSetConstants.CANCELLED_COMMENT, List.of())));
            proposal.setUpdatedBy(actor);
            proposals.update(proposal);
            approvals.find(proposal.getChangeSetId()).ifPresent(approval -> {
                approval.setStatus(ApprovalStatus.CANCELLED);
                approval.setDecidedAt(LocalDateTime.now());
                approval.setComment(ChangeSetConstants.CANCELLED_COMMENT);
                approval.setUpdatedBy(actor);
                approvals.update(approval);
            });
        }
    }
}
