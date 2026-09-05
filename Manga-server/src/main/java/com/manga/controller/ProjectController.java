package com.manga.controller;

import com.manga.common.api.ApiResponse;
import com.manga.common.security.SecurityUtils;
import com.manga.dto.ArtStylePresetResponse;
import com.manga.dto.ProjectMemberResponse;
import com.manga.dto.ProjectCreateRequest;
import com.manga.dto.ProjectResponse;
import com.manga.dto.ProjectScriptImportRequest;
import com.manga.dto.ProjectScriptGenerateRequest;
import com.manga.dto.ProjectScriptResponse;
import com.manga.dto.ProjectScriptSaveRequest;
import com.manga.dto.ProjectWorkflowStageUpdateRequest;
import com.manga.dto.ProjectWorkspaceResponse;
import com.manga.dto.ProjectUpdateRequest;
import com.manga.entity.ProjectWorkflow;
import com.manga.service.ProjectScriptService;
import com.manga.service.ProjectScriptGenerationService;
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
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import reactor.core.publisher.Flux;
import org.springframework.http.codec.ServerSentEvent;

/** 提供当前用户项目的 REST 接口。 */
@RestController
@RequestMapping("/api/v1/projects")
@Slf4j
@RequiredArgsConstructor
public class ProjectController {

    private final ProjectService projectService;
    private final ProjectScriptService scriptService;
    private final ProjectScriptGenerationService scriptGenerationService;
    private final ProjectWorkflowService workflowService;

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
        int sceneCount = script == null ? 0 : script.episodes().stream()
                .mapToInt(episode -> episode.scenes() == null ? 0 : episode.scenes().size())
                .sum();
        ProjectWorkflow workflow = workflowService.getOrCreate(projectId, SecurityUtils.requireCurrentUsername());
        ProjectWorkspaceResponse result = new ProjectWorkspaceResponse(
                project, workflow.getCurrentStage(), workflow.getStageRevision() == null ? 0 : workflow.getStageRevision(),
                script, script == null ? 0 : script.episodes().size(), sceneCount, project.shotCount());
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
    @PutMapping("/{projectId}/script")
    public ApiResponse<ProjectScriptResponse> saveScript(
            @PathVariable long projectId,
            @Valid @RequestBody ProjectScriptSaveRequest request) {
        log.info("[ProjectController#saveScript] request projectId={} subject={} episodeCount={}",
                projectId, SecurityUtils.getCurrentUsername(), request.episodes() == null ? 0 : request.episodes().size());
        ProjectScriptResponse result = scriptService.save(projectId, request);
        log.info("[ProjectController#saveScript] response projectId={} scriptId={} parseStatus={}",
                projectId, result.id(), result.parseStatus());
        return ApiResponse.success(result);
    }

    /** 导入项目剧本原始文本。 */
    @PostMapping("/{projectId}/script/import")
    public ApiResponse<ProjectScriptResponse> importScript(
            @PathVariable long projectId,
            @Valid @RequestBody ProjectScriptImportRequest request) {
        log.info("[ProjectController#importScript] request projectId={} subject={} contentLength={}",
                projectId, SecurityUtils.getCurrentUsername(), request.rawContent().length());
        ProjectScriptResponse result = scriptService.importText(projectId, request.rawContent(), request.sourceType());
        log.info("[ProjectController#importScript] response projectId={} scriptId={}", projectId, result.id());
        return ApiResponse.success(result);
    }

    /** 通过默认 OpenAI 兼容模型流式生成剧本。 */
    @PostMapping("/{projectId}/script/generate")
    public Flux<ServerSentEvent<String>> generateScript(
            @PathVariable long projectId,
            @Valid @RequestBody ProjectScriptGenerateRequest request) {
        log.info("[ProjectController#generateScript] request projectId={} subject={} promptLength={}",
                projectId, SecurityUtils.getCurrentUsername(), request.prompt().length());
        return scriptGenerationService.generate(projectId, request);
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
