package com.manga.controller;

import com.manga.common.api.ApiResponse;
import com.manga.common.security.SecurityUtils;
import com.manga.dto.ArtStylePresetResponse;
import com.manga.dto.ProjectMemberResponse;
import com.manga.dto.ProjectCreateRequest;
import com.manga.dto.ProjectResponse;
import com.manga.dto.ProjectScriptImportRequest;
import com.manga.dto.ProjectScriptChapterImportRequest;
import com.manga.dto.ProjectScriptChapterResponse;
import com.manga.dto.ProjectScriptSaveRequest;
import com.manga.dto.ProjectScriptResponse;
import com.manga.dto.ProjectWorkflowStageUpdateRequest;
import com.manga.dto.ProjectWorkspaceResponse;
import com.manga.dto.ProjectUpdateRequest;
import com.manga.entity.ProjectWorkflow;
import com.manga.service.ProjectScriptService;
import com.manga.service.ScriptTaskService;
import com.manga.service.ProjectWorkflowService;
import com.manga.service.ProjectService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;


/** 提供当前用户项目的 REST 接口。 */
@RestController
@RequestMapping("/api/v1/projects")
@Slf4j
@RequiredArgsConstructor
public class ProjectController {

    private final ProjectService projectService;
    private final ProjectScriptService scriptService;
    private final ProjectWorkflowService workflowService;
    private final ScriptTaskService scriptTaskService;

    /** 查询当前用户可访问的项目列表。 */
    @GetMapping
    public ApiResponse<List<ProjectResponse>> findAll() {
        log.info("[ProjectController#findAll] request subject={}", SecurityUtils.getCurrentUsername());
        List<ProjectResponse> result = projectService.findAll();
        log.info("[ProjectController#findAll] response count={}", result.size());
        return ApiResponse.success(result);
    }

    /** 查询项目可选画风预设。 */
    @GetMapping("/presets/art-styles")
    public ApiResponse<List<ArtStylePresetResponse>> findArtStylePresets() {
        log.info("[ProjectController#findArtStylePresets] request subject={}", SecurityUtils.getCurrentUsername());
        List<ArtStylePresetResponse> result = projectService.findArtStylePresets();
        log.info("[ProjectController#findArtStylePresets] response count={}", result.size());
        return ApiResponse.success(result);
    }

    /** 查询当前用户可访问的项目详情。 */
    @GetMapping("/{projectId}")
    public ApiResponse<ProjectResponse> findById(@PathVariable long projectId) {
        log.info("[ProjectController#findById] request projectId={} subject={}",
                projectId, SecurityUtils.getCurrentUsername());
        ProjectResponse result = projectService.findById(projectId);
        log.info("[ProjectController#findById] response projectId={} shotCount={} status={}",
                result.id(), result.shotCount(), result.status());
        return ApiResponse.success(result);
    }

    /** 查询项目详情工作区及阶段摘要。 */
    @GetMapping("/{projectId}/workspace")
    public ApiResponse<ProjectWorkspaceResponse> findWorkspace(@PathVariable long projectId) {
        log.info("[ProjectController#findWorkspace] request projectId={} subject={}",
                projectId, SecurityUtils.getCurrentUsername());
        ProjectResponse project = projectService.findById(projectId);
        ProjectScriptResponse script = scriptService.find(projectId);
        int sceneCount = script == null ? 0 : script.chapters().stream().mapToInt(com.manga.dto.ProjectScriptChapterSummaryResponse::sceneCount).sum();
        ProjectWorkflow workflow = workflowService.getOrCreate(projectId, SecurityUtils.requireCurrentUsername());
        ProjectWorkspaceResponse result = new ProjectWorkspaceResponse(
                project, workflow.getCurrentStage(), workflow.getStageRevision() == null ? 0 : workflow.getStageRevision(),
                script, script == null ? 0 : script.chapterCount(), sceneCount, project.shotCount(),
                scriptTaskService.list(SecurityUtils.requireCurrentUserId(), false).stream()
                        .filter(task -> task.projectId().equals(projectId)).limit(20).toList());
        log.info("[ProjectController#findWorkspace] response projectId={} stage={} scriptPresent={} shotCount={}",
                projectId, result.workflowStage(), result.script() != null, result.storyboardShotCount());
        return ApiResponse.success(result);
    }

    /** 手动切换项目当前推荐工作流阶段。 */
    @PutMapping("/{projectId}/workflow-stage")
    public ApiResponse<ProjectResponse> updateWorkflowStage(
            @PathVariable long projectId,
            @Valid @RequestBody ProjectWorkflowStageUpdateRequest request) {
        projectService.requireOwnedProject(projectId, SecurityUtils.requireCurrentUserId());
        ProjectWorkflow workflow = workflowService.setStage(
                projectId, request.stage(), SecurityUtils.requireCurrentUsername());
        ProjectResponse result = projectService.findById(projectId);
        log.info("[ProjectController#updateWorkflowStage] response projectId={} stage={} revision={}",
                projectId, workflow.getCurrentStage(), workflow.getStageRevision());
        return ApiResponse.success(result);
    }

    /** 查询项目剧本。 */
    @GetMapping("/{projectId}/script")
    public ApiResponse<ProjectScriptResponse> findScript(@PathVariable long projectId) {
        log.info("[ProjectController#findScript] request projectId={} subject={}",
                projectId, SecurityUtils.getCurrentUsername());
        ProjectScriptResponse result = scriptService.find(projectId);
        log.info("[ProjectController#findScript] response projectId={} present={}", projectId, result != null);
        return ApiResponse.success(result);
    }

    /** 保存项目剧本结构。 */
    @PutMapping(value = "/{projectId}/script", consumes = org.springframework.http.MediaType.APPLICATION_JSON_VALUE)
    public org.springframework.http.ResponseEntity<ApiResponse<com.manga.dto.TaskResponse>> saveScript(
            @PathVariable long projectId,
            @Valid @RequestBody ProjectScriptImportRequest request) {
        return org.springframework.http.ResponseEntity.accepted().body(ApiResponse.success(scriptTaskService.submitImport(projectId, request,
                SecurityUtils.requireCurrentUserId(), SecurityUtils.requireCurrentUsername())));
    }

    /** 导入项目剧本原始文本。 */
    @PostMapping(value = "/{projectId}/script/import", consumes = org.springframework.http.MediaType.APPLICATION_JSON_VALUE)
    public org.springframework.http.ResponseEntity<ApiResponse<com.manga.dto.TaskResponse>> importScript(
            @PathVariable long projectId,
            @Valid @RequestBody ProjectScriptImportRequest request) {
        return org.springframework.http.ResponseEntity.accepted().body(ApiResponse.success(scriptTaskService.submitImport(projectId, request,
                SecurityUtils.requireCurrentUserId(), SecurityUtils.requireCurrentUsername())));
    }

    /** 通过文件上传导入项目剧本原始文本。 */
    @PostMapping(value = "/{projectId}/script/import", consumes = org.springframework.http.MediaType.MULTIPART_FORM_DATA_VALUE)
    public org.springframework.http.ResponseEntity<ApiResponse<com.manga.dto.TaskResponse>> importScript(
            @PathVariable long projectId,
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "sourceType", required = false) String sourceType,
            @RequestParam(value = "afterChapterId", required = false) Long afterChapterId) {
        return org.springframework.http.ResponseEntity.accepted().body(ApiResponse.success(scriptTaskService.submitImport(
                projectId, file, sourceType, afterChapterId, SecurityUtils.requireCurrentUserId(),
                SecurityUtils.requireCurrentUsername())));
    }

    /** 同步导入单个章节。 */
    @PostMapping("/{projectId}/script/chapters/import")
    public ApiResponse<ProjectScriptChapterResponse> importChapter(
            @PathVariable long projectId, @Valid @RequestBody ProjectScriptChapterImportRequest request) {
        return ApiResponse.success(scriptService.createChapter(projectId, request.title(), request.synopsis(),
                request.rawContent(), request.sourceType()));
    }

    /** 查询单章节正文。 */
    @GetMapping("/{projectId}/script/chapters/{chapterId}")
    public ApiResponse<ProjectScriptChapterResponse> findChapter(
            @PathVariable long projectId, @PathVariable long chapterId) {
        return ApiResponse.success(scriptService.findChapter(projectId, chapterId));
    }

    /** 保存单章节正文。 */
    @PutMapping("/{projectId}/script/chapters/{chapterId}")
    public ApiResponse<ProjectScriptChapterResponse> saveChapter(
            @PathVariable long projectId, @PathVariable long chapterId,
            @Valid @RequestBody ProjectScriptSaveRequest request) {
        return ApiResponse.success(scriptService.saveChapter(projectId, chapterId, request));
    }

    /** 删除单章节。 */
    @DeleteMapping("/{projectId}/script/chapters/{chapterId}")
    public ApiResponse<Void> deleteChapter(@PathVariable long projectId, @PathVariable long chapterId) {
        scriptService.deleteChapter(projectId, chapterId);
        return ApiResponse.success(null);
    }

    /** 查询项目任务历史。 */
    @GetMapping("/{projectId}/tasks")
    public ApiResponse<List<com.manga.dto.TaskResponse>> listProjectTasks(@PathVariable long projectId) {
        projectService.requireAccessibleProject(projectId, SecurityUtils.requireCurrentUserId());
        return ApiResponse.success(scriptTaskService.list(SecurityUtils.requireCurrentUserId(), false).stream()
                .filter(task -> task.projectId().equals(projectId)).toList());
    }

    /** 查询项目成员列表。 */
    @GetMapping("/{projectId}/members")
    public ApiResponse<List<ProjectMemberResponse>> findMembers(@PathVariable long projectId) {
        log.info("[ProjectController#findMembers] request projectId={} subject={}",
                projectId, SecurityUtils.getCurrentUsername());
        List<ProjectMemberResponse> result = projectService.findMembers(projectId);
        log.info("[ProjectController#findMembers] response projectId={} count={}", projectId, result.size());
        return ApiResponse.success(result);
    }

    /** 创建项目。 */
    @PostMapping
    public ApiResponse<ProjectResponse> create(
            @Valid @RequestBody ProjectCreateRequest request) {
        log.info("[ProjectController#create] request subject={} name={} status={}",
                SecurityUtils.getCurrentUsername(), request.name(), request.status());
        ProjectResponse result = projectService.create(request);
        log.info("[ProjectController#create] response projectId={} status={}", result.id(), result.status());
        return ApiResponse.success(result);
    }

    /** 更新项目。 */
    @PutMapping("/{projectId}")
    public ApiResponse<ProjectResponse> update(
            @PathVariable long projectId,
            @Valid @RequestBody ProjectUpdateRequest request) {
        log.info("[ProjectController#update] request projectId={} subject={} name={} status={}",
                projectId, SecurityUtils.getCurrentUsername(), request.name(), request.status());
        ProjectResponse result = projectService.update(projectId, request);
        log.info("[ProjectController#update] response projectId={} shotCount={} status={}",
                result.id(), result.shotCount(), result.status());
        return ApiResponse.success(result);
    }

    /** 删除项目。 */
    @DeleteMapping("/{projectId}")
    public ApiResponse<Void> delete(@PathVariable long projectId) {
        log.info("[ProjectController#delete] request projectId={} subject={}",
                projectId, SecurityUtils.getCurrentUsername());
        projectService.delete(projectId);
        log.info("[ProjectController#delete] response projectId={} success={}", projectId, true);
        return ApiResponse.success(null);
    }
}
