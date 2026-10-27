package com.manga.agent.changeset;

import com.manga.common.exception.BusinessException;
import com.manga.entity.CreativeChangeSet;
import com.manga.service.ProjectRevisionService;
import com.manga.service.ProjectScriptService;
import com.manga.service.StoryboardShotService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** 用户审批事务内唯一的业务应用入口；所有内容变更经领域 Service。 */
@Service
@RequiredArgsConstructor
public class ChangeSetApplicationService {
    private final ProjectRevisionService revisions;
    private final ProjectScriptService scripts;
    private final StoryboardShotService storyboards;
    private final ChangeSetCodec codec;

    /** 先比较并锁住基础版本；冲突返回结构化结果，成功写入与审批一起提交或回滚。 */
    @Transactional(propagation = Propagation.MANDATORY)
    public ApprovalResult apply(CreativeChangeSet proposal, long userId, String actor, String comment) {
        if (proposal.getSchemaVersion() != ChangeSetConstants.SCHEMA_VERSION || proposal.getStatus() != ChangeSetStatus.PENDING) {
            throw new BusinessException(ChangeSetResponseCode.STATE_CONFLICT);
        }
        if (!revisions.matches(proposal.getProjectId(), userId, proposal.getBaseProjectRevision(),
                proposal.getChapterId(), proposal.getBaseChapterRevision())) {
            return new ApprovalResult(proposal.getChangeSetId(), ApprovalStatus.CONFLICTED, comment, List.of());
        }
        List<Long> ids = switch (proposal.getKind()) {
            case SCRIPT -> List.of(scripts.applyApproved(proposal.getProjectId(), userId, actor,
                    codec.read(proposal.getPayloadJson(), ScriptChangeSet.class)));
            case STORYBOARD -> storyboards.applyApproved(proposal.getProjectId(), userId, actor,
                    codec.read(proposal.getPayloadJson(), StoryboardChangeSet.class));
        };
        return new ApprovalResult(proposal.getChangeSetId(), ApprovalStatus.APPROVED, comment, ids);
    }
}
