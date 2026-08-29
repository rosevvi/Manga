package com.manga.controller;

import com.manga.common.api.ApiResponse;
import com.manga.common.security.SecurityUtils;
import com.manga.dto.ProjectCreateRequest;
import com.manga.dto.ProjectResponse;
import com.manga.dto.ProjectUpdateRequest;
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

/** 提供当前用户项目的 REST 接口。 */
@RestController
@RequestMapping("/api/v1/projects")
@Slf4j
@RequiredArgsConstructor
public class ProjectController {

    private final ProjectService projectService;

    @GetMapping
    public ApiResponse<List<ProjectResponse>> findAll() {
        log.info("[ProjectController#findAll] request subject={}", SecurityUtils.getCurrentUsername());
        List<ProjectResponse> result = projectService.findAll();
        log.info("[ProjectController#findAll] response count={}", result.size());
        return ApiResponse.success(result);
    }

    @GetMapping("/{projectId}")
    public ApiResponse<ProjectResponse> findById(@PathVariable long projectId) {
        log.info("[ProjectController#findById] request projectId={} subject={}",
                projectId, SecurityUtils.getCurrentUsername());
        ProjectResponse result = projectService.findById(projectId);
        log.info("[ProjectController#findById] response projectId={} shotCount={} status={}",
                result.id(), result.shotCount(), result.status());
        return ApiResponse.success(result);
    }

    @PostMapping
    public ApiResponse<ProjectResponse> create(
            @Valid @RequestBody ProjectCreateRequest request) {
        log.info("[ProjectController#create] request subject={} name={} status={}",
                SecurityUtils.getCurrentUsername(), request.name(), request.status());
        ProjectResponse result = projectService.create(request);
        log.info("[ProjectController#create] response projectId={} status={}", result.id(), result.status());
        return ApiResponse.success(result);
    }

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

    @DeleteMapping("/{projectId}")
    public ApiResponse<Void> delete(@PathVariable long projectId) {
        log.info("[ProjectController#delete] request projectId={} subject={}",
                projectId, SecurityUtils.getCurrentUsername());
        projectService.delete(projectId);
        log.info("[ProjectController#delete] response projectId={} success={}", projectId, true);
        return ApiResponse.success(null);
    }
}
