package com.manga.agent.changeset;

import com.fasterxml.jackson.databind.JsonNode;
import com.manga.agent.collaboration.CreativeReviewGate;
import com.manga.agent.capability.AgentRuntimeContext;
import com.manga.agent.domain.AgentPermissionPolicy;
import com.manga.agent.domain.CreativeActor;
import com.manga.agent.domain.CreativeOperationType;
import com.manga.agent.domain.CreativeRunStatus;
import com.manga.agent.domain.CreativeStepStatus;
import com.manga.agent.event.RunAcceptedEventPayload;
import com.manga.agent.generation.ImageGenerationChange;
import com.manga.agent.generation.VideoGenerationChange;
import com.manga.agent.generation.VideoGenerationQuoteService;
import com.manga.agent.generation.ImageGenerationQuoteService;
import com.manga.common.exception.BusinessException;
import com.manga.dto.ProjectScriptChapterResponse;
import com.manga.entity.CreativeChangeSet;
import com.manga.entity.CreativeRun;
import com.manga.entity.CreativeStep;
import com.manga.entity.MangaProject;
import com.manga.entity.ProjectScriptChapter;
import com.manga.repository.CreativeChangeSetRepository;
import com.manga.repository.CreativeEventRepository;
import com.manga.repository.CreativeRunRepository;
import com.manga.repository.CreativeStepRepository;
import com.manga.repository.StoryboardShotRepository;
import com.manga.service.ProjectRevisionService;
import com.manga.service.ProjectScriptService;
import com.manga.service.StoryboardShotService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** 模型只提交不可变草稿；用户审批和业务写入不在工具调用中执行。 */
@Service
@RequiredArgsConstructor
public class ChangeSetProposalService {
    private final CreativeChangeSetRepository proposals;
    private final CreativeRunRepository runs;
    private final CreativeStepRepository steps;
    private final CreativeEventRepository events;
    private final ProjectRevisionService revisions;
    private final StoryboardShotService storyboards;
    private final ProjectScriptService scripts;
    private final ChangeSetCodec codec;
    private final ImageGenerationQuoteService generationQuotes;
    private final VideoGenerationQuoteService videoQuotes;
    private final StoryboardShotRepository shots;
    private final CreativeReviewGate reviewGate;

    /** 围栏校验、目标归属和 Schema 校验后保存单步骤唯一草稿；重试只返回原草稿。 */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public ProposalReceipt propose(ChangeSetKind kind, JsonNode input, AgentRuntimeContext context) {
        if (Objects.isNull(kind)) throw new BusinessException(ChangeSetResponseCode.INVALID);
        CreativeRun run = runs.lockByRunId(context.runId());
        CreativeStep step = steps.findByStepId(context.stepId()).orElse(null);
        requireOwner(run, step, context);
        reviewGate.requireCandidate(step, kind, input);
        CreativeChangeSet existing = proposals.findByStep(context.stepId()).orElse(null);
        if (Objects.nonNull(existing)) {
            if (existing.getStatus() != ChangeSetStatus.DRAFT) {
                throw new BusinessException(ChangeSetResponseCode.ALREADY_PROPOSED, HttpStatus.CONFLICT);
            }
            return new ProposalReceipt(existing.getChangeSetId(), existing.getStatus());
        }
        Object payload;
        Long chapterId;
        Long mediaShotId = null;
        if (kind == ChangeSetKind.SCRIPT) {
            ScriptChangeSet script = codec.validate(input, ScriptChangeSet.class);
            payload = script;
            chapterId = script.chapterId();
        } else if (kind == ChangeSetKind.STORYBOARD) {
            StoryboardChangeSet storyboard = codec.validate(input, StoryboardChangeSet.class);
            payload = storyboard;
            chapterId = storyboard.chapterId();
        } else if (kind == ChangeSetKind.VIDEO_GENERATION) {
            VideoGenerationChange video = videoQuotes.canonicalize(codec.validate(input, VideoGenerationChange.class), run.getUserId());
            payload = video;
            mediaShotId = video.shotId();
            chapterId = mediaChapter(run, mediaShotId);
        } else {
            ImageGenerationChange image = generationQuotes.canonicalize(codec.validate(input, ImageGenerationChange.class), run.getUserId());
            payload = image;
            mediaShotId = image.shotId();
            chapterId = mediaChapter(run, mediaShotId);
        }
        if ((run.getOperationType() == CreativeOperationType.GENERATE_CHAPTER_STORYBOARD
                && (kind != ChangeSetKind.STORYBOARD || !Objects.equals(chapterId, run.getTargetId())))
                || (run.getOperationType() == CreativeOperationType.GENERATE_SCRIPT_CHAPTER
                && (kind != ChangeSetKind.SCRIPT || Objects.nonNull(chapterId)))
                || (run.getOperationType() == CreativeOperationType.GENERATE_SHOT_IMAGE
                && (kind != ChangeSetKind.IMAGE_GENERATION || !Objects.equals(mediaShotId, run.getTargetId())))
                || (run.getOperationType() == CreativeOperationType.GENERATE_SHOT_VIDEO
                && (kind != ChangeSetKind.VIDEO_GENERATION || !Objects.equals(mediaShotId, run.getTargetId())))) {
            throw new BusinessException(ChangeSetResponseCode.INVALID);
        }
        MangaProject project = revisions.lock(run.getProjectId(), run.getUserId());
        if (!Objects.equals(project.getRevision(), step.getBaseProjectRevision())) {
            throw new BusinessException(ChangeSetResponseCode.REVISION_CONFLICT, HttpStatus.CONFLICT);
        }
        ProjectScriptChapter chapter = Objects.isNull(chapterId) ? null : revisions.chapter(project.getId(), chapterId);
        Object before;
        if (kind == ChangeSetKind.SCRIPT) {
            ProjectScriptChapterResponse current = Objects.isNull(chapter) ? null
                    : scripts.findChapterForUser(project.getId(), chapterId, run.getUserId());
            before = Objects.isNull(current) ? null : new ScriptChangeSet(chapterId, current.title(), current.synopsis(), current.rawContent(), current.scenes());
        } else if (kind == ChangeSetKind.STORYBOARD) {
            before = storyboards.findAllForUser(project.getId(), run.getUserId(), chapterId);
        } else {
            before = shots.findByProjectAndId(project.getId(), mediaShotId).orElseThrow();
        }
        String actor = CreativeActor.user(context.userId());
        CreativeChangeSet proposal = CreativeChangeSet.builder().changeSetId(UUID.randomUUID().toString())
                .runId(run.getRunId()).stepId(step.getStepId()).userId(run.getUserId()).projectId(project.getId())
                .chapterId(chapterId).kind(kind).schemaVersion(ChangeSetConstants.SCHEMA_VERSION).status(ChangeSetStatus.DRAFT)
                .instruction(codec.read(events.findBatchAfter(run.getRunId(), 0L, 1).getFirst().getPayloadJson(), RunAcceptedEventPayload.class).instruction())
                .baseProjectRevision(step.getBaseProjectRevision())
                .baseChapterRevision(Objects.isNull(chapter) ? null : chapter.getRevision())
                .beforeJson(codec.write(before)).payloadJson(codec.write(payload)).createdBy(actor).updatedBy(actor).build();
        proposals.create(proposal);
        return new ProposalReceipt(proposal.getChangeSetId(), proposal.getStatus());
    }

    /** 媒体目标必须属于当前项目。 */
    private Long mediaChapter(CreativeRun run, Long shotId) {
        return shots.findByProjectAndId(run.getProjectId(), shotId)
                .orElseThrow(() -> new BusinessException(ChangeSetResponseCode.NOT_FOUND)).getChapterId();
    }

    /** 只有仍持有租约、项目及提案权限的真实执行上下文可以生成草稿。 */
    private void requireOwner(CreativeRun run, CreativeStep step, AgentRuntimeContext context) {
        if (Objects.isNull(run) || Objects.isNull(step) || run.getStatus() != CreativeRunStatus.ACTIVE
                || run.getUserId() != context.userId() || !Objects.equals(run.getProjectId(), context.projectId())
                || Objects.isNull(run.getProjectId()) || !Objects.equals(step.getRunId(), run.getRunId())
                || step.getStatus() != CreativeStepStatus.RUNNING || !Objects.equals(step.getOwnerInstanceId(), context.ownerInstanceId())
                || step.getOwnerEpoch() != context.ownerEpoch() || Objects.isNull(step.getLeaseUntil())
                || !step.getLeaseUntil().isAfter(LocalDateTime.now())
                || !run.getDeadlineAt().isAfter(LocalDateTime.now())
                || !Objects.equals(step.getStateSessionId(), context.stateSessionId())
                || !run.getPermissionPolicy().allows(AgentPermissionPolicy.PROPOSE_CHANGES)
                || Objects.nonNull(step.getApprovalResultJson())) {
            throw new BusinessException(ChangeSetResponseCode.OWNER_LOST, HttpStatus.CONFLICT);
        }
    }

    /** 只返回草稿标识，工具结果不代表审批成功。 */
    public record ProposalReceipt(
            /** 持久化提案标识。 */ String changeSetId,
            /** 尚未开放审批的草稿状态。 */ ChangeSetStatus status
    ) { }
}
