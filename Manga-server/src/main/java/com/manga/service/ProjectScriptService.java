package com.manga.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.manga.common.enums.ProjectWorkflowStage;
import com.manga.common.enums.ScriptChapterParseStatus;
import com.manga.common.exception.BusinessException;
import com.manga.common.security.SecurityUtils;
import com.manga.dto.ProjectScriptChapterResponse;
import com.manga.dto.ProjectScriptChapterSummaryResponse;
import com.manga.dto.ProjectScriptResponse;
import com.manga.dto.ProjectScriptSaveRequest;
import com.manga.entity.MangaProject;
import com.manga.entity.ProjectScript;
import com.manga.entity.ProjectScriptChapter;
import com.manga.entity.ProjectScriptDialogue;
import com.manga.entity.ProjectScriptScene;
import com.manga.mapper.ProjectScriptDialogueMapper;
import com.manga.mapper.ProjectScriptSceneMapper;
import com.manga.repository.ProjectScriptChapterRepository;
import com.manga.repository.ProjectScriptRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

import static com.manga.common.enums.ProjectResponseCode.PROJECT_NOT_FOUND;
import static com.manga.common.enums.ProjectResponseCode.SCRIPT_INVALID;

/** 管理项目剧本元数据和单章节内容。 */
@Service
@RequiredArgsConstructor
public class ProjectScriptService {

    private final ProjectService projectService;
    private final ProjectScriptRepository scriptRepository;
    private final ProjectScriptChapterRepository chapterRepository;
    private final ProjectScriptSceneMapper sceneMapper;
    private final ProjectScriptDialogueMapper dialogueMapper;
    private final ProjectWorkflowService workflowService;
    private final ObjectMapper objectMapper;

    /** 查询剧本元数据和章节摘要，不返回正文。 */
    public ProjectScriptResponse find(long projectId) {
        requireAccessibleProject(projectId);
        ProjectScript script = scriptRepository.findByProjectId(projectId).orElse(null);
        if (script == null) return null;
        List<ProjectScriptChapter> chapters = chapterRepository.findSummaries(script.getId());
        List<ProjectScriptChapterSummaryResponse> summaries = new ArrayList<>();
        for (int index = 0; index < chapters.size(); index++) {
            ProjectScriptChapter chapter = chapters.get(index);
            summaries.add(new ProjectScriptChapterSummaryResponse(chapter.getId(), index + 1, chapter.getTitle(),
                    chapter.getSynopsis(), chapter.getParseStatus(), chapter.getRawContentLength() == null ? 0 : chapter.getRawContentLength(),
                    sceneMapper.findByChapterId(chapter.getId()).size(), chapter.getSortOrder(), chapter.getUpdatedAt()));
        }
        return new ProjectScriptResponse(script.getId(), script.getProjectId(), script.getTitle(), script.getSynopsis(),
                script.getSourceType(), script.getParseStatus(), script.getStructureVersion(), summaries.size(), summaries,
                script.getLastError(), script.getCreatedAt(), script.getUpdatedAt());
    }

    /** 查询单章节正文和结构。 */
    public ProjectScriptChapterResponse findChapter(long projectId, long chapterId) {
        ProjectScript script = requireScript(projectId, false);
        ProjectScriptChapter chapter = chapterRepository.findByIdAndScript(chapterId, script.getId())
                .orElseThrow(() -> new BusinessException(PROJECT_NOT_FOUND, HttpStatus.NOT_FOUND));
        return toChapterResponse(chapter, chapterNumber(script.getId(), chapter.getId()));
    }

    /** 同步创建一个章节。 */
    @Transactional
    public ProjectScriptChapterResponse createChapter(long projectId, String title, String synopsis,
            String rawContent, String sourceType) {
        ProjectScript script = requireScript(projectId, true);
        ProjectScriptChapter chapter = createChapterEntity(script, title, synopsis, rawContent, sourceType,
                nextSortOrder(script.getId()), ScriptChapterParseStatus.COMPLETED);
        workflowService.advanceTo(projectId, ProjectWorkflowStage.STORYBOARD, SecurityUtils.requireCurrentUsername());
        return toChapterResponse(chapter, chapterNumber(script.getId(), chapter.getId()));
    }

    /** 保存单章节正文和场景结构。 */
    @Transactional
    public ProjectScriptChapterResponse saveChapter(long projectId, long chapterId, ProjectScriptSaveRequest request) {
        ProjectScript script = requireScript(projectId, true);
        ProjectScriptChapter chapter = chapterRepository.findByIdAndScript(chapterId, script.getId())
                .orElseThrow(() -> new BusinessException(PROJECT_NOT_FOUND, HttpStatus.NOT_FOUND));
        chapter.setTitle(request.title().trim());
        chapter.setSynopsis(normalize(request.synopsis()));
        chapter.setRawContent(request.rawContent().trim());
        chapter.setSourceType(normalizeOr(request.sourceType(), "MANUAL"));
        chapter.setParseStatus(ScriptChapterParseStatus.COMPLETED);
        chapter.setStructureVersion(chapter.getStructureVersion() == null ? 1 : chapter.getStructureVersion() + 1);
        chapter.setStructureJson(serializeStructure(request.scenes()));
        chapter.setLastError(null);
        chapter.setUpdatedBy(SecurityUtils.requireCurrentUsername());
        chapterRepository.update(chapter);
        replaceScenes(chapter, request.scenes());
        refreshChapterCount(script);
        workflowService.advanceTo(projectId, ProjectWorkflowStage.STORYBOARD, SecurityUtils.requireCurrentUsername());
        return toChapterResponse(chapter, chapterNumber(script.getId(), chapter.getId()));
    }

    /** 删除一个章节。 */
    @Transactional
    public void deleteChapter(long projectId, long chapterId) {
        ProjectScript script = requireScript(projectId, true);
        ProjectScriptChapter chapter = chapterRepository.findByIdAndScript(chapterId, script.getId())
                .orElseThrow(() -> new BusinessException(PROJECT_NOT_FOUND, HttpStatus.NOT_FOUND));
        chapterRepository.delete(chapter.getId());
        refreshChapterCount(script);
    }

    /** 确保项目拥有剧本元数据记录。 */
    @Transactional
    public ProjectScript ensureScript(long projectId) {
        return ensureScriptForOwner(projectId, SecurityUtils.requireCurrentUserId(), SecurityUtils.requireCurrentUsername());
    }

    /** 为后台任务创建或读取剧本元数据。 */
    @Transactional
    public ProjectScript ensureScriptForOwner(long projectId, long ownerUserId, String actor) {
        MangaProject project = projectService.requireOwnedProject(projectId, ownerUserId);
        return scriptRepository.findByProjectId(projectId).orElseGet(() -> scriptRepository.create(ProjectScript.builder()
                .projectId(projectId).title(project.getName()).sourceType("MANUAL").parseStatus("DRAFT")
                .structureVersion(1).chapterCount(0).createdBy(actor).updatedBy(actor).build()));
    }

    /** 为后台任务创建章节。 */
    @Transactional
    public ProjectScriptChapter createTaskChapter(long projectId, String title, String synopsis, String rawContent,
            String sourceType, int sortOrder, long ownerUserId, String actor) {
        ProjectScript script = ensureScriptForOwner(projectId, ownerUserId, actor);
        ProjectScriptChapter chapter = createChapterEntity(script, title, synopsis, rawContent, sourceType, sortOrder,
                ScriptChapterParseStatus.COMPLETED, actor);
        refreshChapterCount(script);
        return chapter;
    }

    /** 返回插入章节的稳定排序值，并为后续章节预留空间。 */
    @Transactional
    public int prepareInsertOrder(long projectId, Long afterChapterId, int amount, long ownerUserId) {
        ProjectScript script = ensureScriptForOwner(projectId, ownerUserId, SecurityUtils.requireCurrentUsername());
        int sortOrder = 1;
        if (afterChapterId != null) {
            ProjectScriptChapter after = chapterRepository.findByIdAndScript(afterChapterId, script.getId())
                    .orElse(null);
            if (after != null) sortOrder = after.getSortOrder() + 1;
        } else {
            sortOrder = nextSortOrder(script.getId());
        }
        if (amount > 0) chapterRepository.shiftSortOrders(script.getId(), sortOrder, amount);
        return sortOrder;
    }

    private ProjectScriptChapter createChapterEntity(ProjectScript script, String title, String synopsis,
            String rawContent, String sourceType, int sortOrder, ScriptChapterParseStatus status) {
        return createChapterEntity(script, title, synopsis, rawContent, sourceType, sortOrder, status,
                SecurityUtils.requireCurrentUsername());
    }

    private ProjectScriptChapter createChapterEntity(ProjectScript script, String title, String synopsis,
            String rawContent, String sourceType, int sortOrder, ScriptChapterParseStatus status, String actor) {
        return chapterRepository.create(ProjectScriptChapter.builder().scriptId(script.getId())
                .title(normalizeOr(title, "未命名章节")).synopsis(normalize(synopsis)).rawContent(rawContent.trim())
                .sourceType(normalizeOr(sourceType, "MANUAL")).parseStatus(status).structureVersion(1).sortOrder(sortOrder)
                .createdBy(actor).updatedBy(actor).build());
    }

    private void replaceScenes(ProjectScriptChapter chapter, List<ProjectScriptSaveRequest.Scene> scenes) {
        dialogueMapper.deleteByChapterId(chapter.getId());
        sceneMapper.deleteByChapterId(chapter.getId());
        if (scenes == null) return;
        for (int index = 0; index < scenes.size(); index++) {
            ProjectScriptSaveRequest.Scene scene = scenes.get(index);
            ProjectScriptScene entity = ProjectScriptScene.builder().chapterId(chapter.getId())
                    .sceneNumber(scene.sceneNumber() == null ? index + 1 : scene.sceneNumber()).location(normalize(scene.location()))
                    .timeDescription(normalize(scene.time())).summary(normalize(scene.summary())).content(normalize(scene.content()))
                    .sortOrder(index + 1).build();
            sceneMapper.insert(entity);
            if (scene.dialogues() == null) continue;
            for (int dialogueIndex = 0; dialogueIndex < scene.dialogues().size(); dialogueIndex++) {
                ProjectScriptSaveRequest.Dialogue dialogue = scene.dialogues().get(dialogueIndex);
                dialogueMapper.insert(ProjectScriptDialogue.builder().sceneId(entity.getId()).speaker(normalize(dialogue.speaker()))
                        .text(normalizeOr(dialogue.text(), "")).dialogueType(normalizeOr(dialogue.type(), "DIALOGUE"))
                        .sortOrder(dialogueIndex + 1).build());
            }
        }
    }

    private ProjectScriptChapterResponse toChapterResponse(ProjectScriptChapter chapter, int number) {
        List<ProjectScriptScene> scenes = sceneMapper.findByChapterId(chapter.getId());
        List<ProjectScriptSaveRequest.Scene> result = scenes.stream().map(scene -> new ProjectScriptSaveRequest.Scene(
                scene.getSceneNumber(), scene.getLocation(), scene.getTimeDescription(), scene.getSummary(), scene.getContent(),
                dialogueMapper.findByChapterId(chapter.getId()).stream().filter(d -> d.getSceneId().equals(scene.getId()))
                        .map(d -> new ProjectScriptSaveRequest.Dialogue(d.getSpeaker(), d.getText(), d.getDialogueType())).toList())).toList();
        return new ProjectScriptChapterResponse(chapter.getId(), chapter.getScriptId(), number, chapter.getTitle(), chapter.getSynopsis(),
                chapter.getRawContent(), chapter.getSourceType(), chapter.getParseStatus(), chapter.getStructureVersion(), result,
                chapter.getLastError(), chapter.getSortOrder(), chapter.getUpdatedAt());
    }

    private int chapterNumber(long scriptId, long chapterId) {
        List<ProjectScriptChapter> chapters = chapterRepository.findSummaries(scriptId);
        for (int index = 0; index < chapters.size(); index++) if (chapters.get(index).getId().equals(chapterId)) return index + 1;
        return 1;
    }

    private int nextSortOrder(long scriptId) {
        return chapterRepository.findSummaries(scriptId).stream().mapToInt(ProjectScriptChapter::getSortOrder).max().orElse(0) + 1;
    }

    private void refreshChapterCount(ProjectScript script) {
        scriptRepository.updateChapterCount(script.getId(), chapterRepository.findSummaries(script.getId()).size());
    }

    private ProjectScript requireScript(long projectId, boolean owned) {
        if (owned) return ensureScript(projectId);
        projectService.requireAccessibleProject(projectId, SecurityUtils.requireCurrentUserId());
        return scriptRepository.findByProjectId(projectId)
                .orElseThrow(() -> new BusinessException(PROJECT_NOT_FOUND, HttpStatus.NOT_FOUND));
    }

    private String serializeStructure(List<ProjectScriptSaveRequest.Scene> scenes) {
        try { return objectMapper.writeValueAsString(scenes == null ? List.of() : scenes); }
        catch (JsonProcessingException exception) { throw new BusinessException(SCRIPT_INVALID, "章节结构序列化失败", HttpStatus.BAD_REQUEST); }
    }

    private String normalize(String value) { return StringUtils.hasText(value) ? value.trim() : null; }
    private String normalizeOr(String value, String fallback) { return StringUtils.hasText(value) ? value.trim() : fallback; }
    private void requireAccessibleProject(long projectId) { projectService.requireAccessibleProject(projectId, SecurityUtils.requireCurrentUserId()); }
}
