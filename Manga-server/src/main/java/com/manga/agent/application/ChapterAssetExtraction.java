package com.manga.agent.application;

import com.manga.agent.api.CreativeRuntimeResponseCode;
import com.manga.agent.capability.AgentRuntimeContext;
import com.manga.agent.changeset.ChangeSetCodec;
import com.manga.agent.changeset.ChangeSetResponseCode;
import com.manga.agent.collaboration.CollaborationGuard;
import com.manga.agent.collaboration.CreativeStepResult;
import com.manga.agent.collaboration.StepResultKind;
import com.manga.agent.domain.*;
import com.manga.agent.harness.AgentRuntimeException;
import com.manga.common.exception.BusinessException;
import com.manga.dto.ProjectAssetContracts.AssetSummary;
import com.manga.dto.ProjectAssetContracts.RecognizedAsset;
import com.manga.entity.CreativeRun;
import com.manga.entity.CreativeStep;
import com.manga.repository.*;
import com.manga.service.ProjectAssetService;
import com.manga.service.ProjectRevisionService;
import com.manga.service.ProjectService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import java.util.List;
import java.util.Objects;

/** 提供绑定当前章节的读取与登记操作。写入事务同时保存资产、章节关联和可恢复的工具回执。 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ChapterAssetExtraction {
    public static final int MAX_ASSETS = 200;
    private static final int PAGE_CHARS = 12000;
    private final CreativeRunRepository runs;
    private final CreativeStepRepository steps;
    private final CreativeGraphRepository graph;
    private final ProjectRevisionService revisions;
    private final ProjectAssetService assets;
    private final ProjectService projects;
    private final ProjectScriptChapterRepository chapters;
    private final ChangeSetCodec codec;

    /** 获取当前用户在此章最近一次识别进度，历史章节允许没有识别任务。 */
    @Transactional(readOnly = true)
    public Attempt latest(long projectId, long chapterId, long userId) {
        projects.requireAccessibleProject(projectId, userId);
        return runs.findLatestManualForTarget(userId, projectId, chapterId, CreativeOperationType.EXTRACT_CHAPTER_ASSETS, null)
                .map(run -> new Attempt(run.getRunId(), run.getStatus(), run.getErrorMessage())).orElse(null);
    }

    /** 从可信 Run 获取章节和用户，长章节分段读取，只有首页附带项目目录。 */
    @Transactional(readOnly = true)
    public Source read(AgentRuntimeContext context, int offset) {
        var run = runs.findByRunId(context.runId()).orElse(null);
        checkScope(run, steps.findByStepId(context.stepId()).orElse(null), context);
        projects.requireOwnedProject(run.getProjectId(), run.getUserId());
        var chapter = revisions.chapter(run.getProjectId(), run.getTargetId());
        String body = chapter.getRawContent();
        int from = Math.min(offset, body.length());
        int end = from + Math.min(PAGE_CHARS, body.length() - from);
        if (end < body.length() && Character.isHighSurrogate(body.charAt(end - 1))) end--;
        return new Source(chapter.getTitle(), chapter.getRevision(), body.substring(from, end),
                end < body.length() ? end : null, offset == 0 ? assets.catalog(run.getProjectId(), run.getUserId()) : List.of());
    }

    /** 校验版本和执行租约，重放同一步时直接返回第一次已提交的回执。 */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public String register(AgentRuntimeContext context, Submission submission) {
        var run = runs.lockByRunId(context.runId());
        var step = steps.findByStepId(context.stepId()).orElse(null);
        checkScope(run, step, context);
        if (step.getResultJson() != null) return codec.read(step.getResultJson(), CreativeStepResult.class).content();
        long projectId = run.getProjectId(), chapterId = run.getTargetId(), userId = run.getUserId();
        revisions.lock(projectId, userId);
        var chapter = revisions.chapter(projectId, chapterId);
        if (!Objects.equals(submission.revision(), chapter.getRevision())) {
            throw new BusinessException(ChangeSetResponseCode.REVISION_CONFLICT, HttpStatus.CONFLICT);
        }
        int count = assets.registerRecognized(projectId, chapterId, userId, submission.assets());
        String actor = CreativeActor.user(userId);
        if (!StringUtils.hasText(chapter.getSynopsis())) {
            revisions.edit(projectId, userId, null, actor);
            chapter.setSynopsis(submission.synopsis().strip());
            chapter.setUpdatedBy(actor);
            chapters.update(chapter);
        }
        String summary = "本章已识别 " + count + " 项资产";
        var result = new CreativeStepResult(StepResultKind.ANALYSIS, summary, null, null, List.of(), List.of());
        if (!graph.saveResult(step.getStepId(), context.ownerInstanceId(), context.ownerEpoch(), codec.write(result), actor)) {
            throw new AgentRuntimeException(CreativeErrorCode.OWNER_LEASE_LOST, CreativeRuntimeMessages.AGENT_STEP_OWNER_LEASE_LOST);
        }
        log.info("Chapter assets registered projectId={} chapterId={} runId={} count={}", projectId, chapterId, run.getRunId(), count);
        return summary;
    }

    private void checkScope(CreativeRun run, CreativeStep step, AgentRuntimeContext context) {
        CollaborationGuard.requireRun(run, step, context);
        if (step.getParentStepId() != null || run.getOperationType() != CreativeOperationType.EXTRACT_CHAPTER_ASSETS
                || run.getTargetType() != CreativeResourceType.CHAPTER || run.getTargetId() == null
                || context.permissionPolicy() != AgentPermissionPolicy.AUTOMATION || run.getPermissionPolicy() != context.permissionPolicy()) {
            throw new BusinessException(CreativeRuntimeResponseCode.TARGET_RESOURCE_INVALID, HttpStatus.FORBIDDEN);
        }
    }

    /** 模型从工具获取的正文分段及项目级资产标识。 */
    public record Source(String title, long revision, String content, Integer nextOffset, List<AssetSummary> catalog) {}
    /** 当前章节最近一次运行的公开状态。 */
    public record Attempt(String runId, CreativeRunStatus status, String errorMessage) {}
    /** 整章一次提交，空列表代表读完正文后没有识别出资产。 */
    public record Submission(@NotNull @Positive Long revision, @NotBlank @Size(max = 2000) String synopsis,
                             @NotNull @Size(max = MAX_ASSETS) List<@NotNull @Valid RecognizedAsset> assets) {}
}
