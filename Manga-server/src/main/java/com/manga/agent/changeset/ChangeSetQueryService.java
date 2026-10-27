package com.manga.agent.changeset;

import com.manga.common.exception.BusinessException;
import com.manga.entity.CreativeApproval;
import com.manga.entity.CreativeChangeSet;
import com.manga.repository.CreativeApprovalRepository;
import com.manga.repository.CreativeChangeSetRepository;
import com.manga.repository.CreativeConversationRepository;
import com.manga.repository.CreativeRunRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/** 以认证用户和会话或运行范围查询提案，批量装配审批避免 N+1。 */
@Service
@RequiredArgsConstructor
public class ChangeSetQueryService {
    /** 审批预览列表单页上限。 */
    private static final int PAGE_SIZE = 100;
    private final CreativeChangeSetRepository proposals;
    private final CreativeApprovalRepository approvals;
    private final CreativeRunRepository runs;
    private final CreativeConversationRepository conversations;
    private final ChangeSetCodec codec;

    /** 会话入口与手动运行入口共用查询；必须且只能提供一个范围。 */
    @Transactional(readOnly = true)
    public List<ChangeSetView> list(long userId, String runId, String conversationId, Long beforeId) {
        if (Objects.isNull(runId) == Objects.isNull(conversationId)) throw new BusinessException(ChangeSetResponseCode.INVALID);
        if (Objects.nonNull(runId)) runs.findOwned(runId, userId)
                .orElseThrow(() -> new BusinessException(ChangeSetResponseCode.NOT_FOUND, HttpStatus.NOT_FOUND));
        if (Objects.nonNull(conversationId)) conversations.findOwned(conversationId, userId)
                .orElseThrow(() -> new BusinessException(ChangeSetResponseCode.NOT_FOUND, HttpStatus.NOT_FOUND));
        List<CreativeChangeSet> page = proposals.findVisible(userId, runId, conversationId, beforeId, PAGE_SIZE);
        if (page.isEmpty()) return List.of();
        Map<String, CreativeApproval> indexed = approvals.findAll(page.stream().map(CreativeChangeSet::getChangeSetId).toList())
                .stream().collect(Collectors.toMap(CreativeApproval::getChangeSetId, Function.identity()));
        return page.stream().map(proposal -> view(proposal, indexed.get(proposal.getChangeSetId()))).toList();
    }

    /** 单条查询返回冻结差异和审批事实。 */
    @Transactional(readOnly = true)
    public ChangeSetView find(long userId, String id) {
        CreativeChangeSet proposal = proposals.find(id).filter(value -> value.getUserId() == userId && value.getStatus() != ChangeSetStatus.DRAFT)
                .orElseThrow(() -> new BusinessException(ChangeSetResponseCode.NOT_FOUND, HttpStatus.NOT_FOUND));
        return view(proposal, approvals.find(id).orElse(null));
    }

    /** 公开投影只包含审查所需业务字段。 */
    private ChangeSetView view(CreativeChangeSet proposal, CreativeApproval approval) {
        return new ChangeSetView(proposal.getId(), proposal.getChangeSetId(), proposal.getRunId(), proposal.getStepId(),
                proposal.getProjectId(), proposal.getChapterId(), proposal.getKind(), proposal.getSchemaVersion(), proposal.getInstruction(), proposal.getStatus(),
                proposal.getBaseProjectRevision(), proposal.getBaseChapterRevision(), codec.tree(proposal.getBeforeJson()), codec.tree(proposal.getPayloadJson()),
                Objects.isNull(approval) ? null : approval.getStatus(), Objects.isNull(approval) ? null : approval.getExpiresAt(),
                Objects.isNull(approval) ? null : approval.getDecidedBy(), Objects.isNull(approval) ? null : approval.getDecidedAt(),
                Objects.isNull(approval) ? null : approval.getComment());
    }
}
