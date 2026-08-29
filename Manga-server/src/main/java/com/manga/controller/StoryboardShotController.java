package com.manga.controller;

import com.manga.common.api.ApiResponse;
import com.manga.common.security.SecurityUtils;
import com.manga.dto.StoryboardShotCreateRequest;
import com.manga.dto.StoryboardShotOrderRequest;
import com.manga.dto.StoryboardShotResponse;
import com.manga.dto.StoryboardShotUpdateRequest;
import com.manga.service.StoryboardShotService;
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

/** 提供项目分镜镜头的 REST 接口。 */
@RestController
@RequestMapping("/api/v1/projects")
@Slf4j
@RequiredArgsConstructor
public class StoryboardShotController {

    private final StoryboardShotService storyboardShotService;

    @GetMapping("/{projectId}/storyboard-shots")
    public ApiResponse<List<StoryboardShotResponse>> findAll(@PathVariable long projectId) {
        log.info("[StoryboardShotController#findAll] request projectId={} subject={}",
                projectId, SecurityUtils.getCurrentUsername());
        List<StoryboardShotResponse> result = storyboardShotService.findAll(projectId);
        log.info("[StoryboardShotController#findAll] response projectId={} count={}", projectId, result.size());
        return ApiResponse.success(result);
    }

    @PostMapping("/{projectId}/storyboard-shots")
    public ApiResponse<StoryboardShotResponse> create(
            @PathVariable long projectId,
            @Valid @RequestBody StoryboardShotCreateRequest request) {
        log.info("[StoryboardShotController#create] request projectId={} subject={} title={} status={}",
                projectId, SecurityUtils.getCurrentUsername(), request.title(), request.status());
        StoryboardShotResponse result = storyboardShotService.create(projectId, request);
        log.info("[StoryboardShotController#create] response projectId={} shotId={} sortOrder={}",
                projectId, result.id(), result.sortOrder());
        return ApiResponse.success(result);
    }

    @PutMapping("/{projectId}/storyboard-shots/{shotId}")
    public ApiResponse<StoryboardShotResponse> update(
            @PathVariable long projectId,
            @PathVariable long shotId,
            @Valid @RequestBody StoryboardShotUpdateRequest request) {
        log.info("[StoryboardShotController#update] request projectId={} shotId={} subject={} status={}",
                projectId, shotId, SecurityUtils.getCurrentUsername(), request.status());
        StoryboardShotResponse result = storyboardShotService.update(projectId, shotId, request);
        log.info("[StoryboardShotController#update] response projectId={} shotId={} status={}",
                projectId, result.id(), result.status());
        return ApiResponse.success(result);
    }

    @DeleteMapping("/{projectId}/storyboard-shots/{shotId}")
    public ApiResponse<Void> delete(@PathVariable long projectId, @PathVariable long shotId) {
        log.info("[StoryboardShotController#delete] request projectId={} shotId={} subject={}",
                projectId, shotId, SecurityUtils.getCurrentUsername());
        storyboardShotService.delete(projectId, shotId);
        log.info("[StoryboardShotController#delete] response projectId={} shotId={} success={}",
                projectId, shotId, true);
        return ApiResponse.success(null);
    }

    @PutMapping("/{projectId}/storyboard-shots/order")
    public ApiResponse<List<StoryboardShotResponse>> reorder(
            @PathVariable long projectId,
            @Valid @RequestBody StoryboardShotOrderRequest request) {
        log.info("[StoryboardShotController#reorder] request projectId={} subject={} shotCount={}",
                projectId, SecurityUtils.getCurrentUsername(), request.shotIds().size());
        List<StoryboardShotResponse> result = storyboardShotService.reorder(projectId, request);
        log.info("[StoryboardShotController#reorder] response projectId={} count={}", projectId, result.size());
        return ApiResponse.success(result);
    }
}
