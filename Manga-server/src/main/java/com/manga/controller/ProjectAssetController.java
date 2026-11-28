package com.manga.controller;

import com.manga.common.api.ApiResponse;
import com.manga.agent.application.ChapterAssetExtraction;
import com.manga.common.security.SecurityUtils;
import com.manga.dto.ProjectAssetContracts.*;
import com.manga.dto.StoryboardImageSnapshot;
import com.manga.service.ProjectAssetService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** 提供项目资产目录、章内挂接和图片草稿接口。 */
@RestController
@RequestMapping("/api/v1/projects/{projectId}")
@RequiredArgsConstructor
@Slf4j
public class ProjectAssetController {
    private final ProjectAssetService assets;
    private final ChapterAssetExtraction extraction;

    /** 查看导入识别进度与错误，旧章节可以没有任务。 */
    @GetMapping("/script/chapters/{chapterId}/assets/extraction")
    public ApiResponse<ChapterAssetExtraction.Attempt> extraction(@PathVariable long projectId, @PathVariable long chapterId) {
        return ApiResponse.success(extraction.latest(projectId, chapterId, userId()));
    }

    /** 查询项目内可复用的资产。 */
    @GetMapping("/assets")
    public ApiResponse<List<AssetSummary>> catalog(@PathVariable @Positive long projectId) {
        return ApiResponse.success(assets.catalog(projectId, userId()));
    }

    /** 获取章节资产及其图片项。 */
    @GetMapping("/script/chapters/{chapterId}/assets")
    public ApiResponse<List<Asset>> chapter(@PathVariable @Positive long projectId, @PathVariable @Positive long chapterId) {
        return ApiResponse.success(assets.chapter(projectId, chapterId, userId()));
    }

    /** 新建项目资产，可同时加入指定章节。 */
    @PostMapping("/assets")
    public ApiResponse<Created> create(@PathVariable @Positive long projectId, @Valid @RequestBody CreateAsset request) {
        long id = assets.create(projectId, userId(), request);
        log.info("Project asset created projectId={} assetId={} kind={} chapterId={}", projectId, id, request.assetKind(), request.chapterId());
        return ApiResponse.success(new Created(id));
    }

    /** 编辑项目资产资料。 */
    @PutMapping("/assets/{assetId}")
    public ApiResponse<Void> edit(@PathVariable @Positive long projectId, @PathVariable @Positive long assetId,
            @Valid @RequestBody Fields request) {
        assets.edit(projectId, assetId, userId(), request);
        return ApiResponse.success(null);
    }

    /** 将已有项目资产加入章节。 */
    @PutMapping("/script/chapters/{chapterId}/assets/{assetId}")
    public ApiResponse<Void> attach(@PathVariable @Positive long projectId, @PathVariable @Positive long chapterId,
            @PathVariable @Positive long assetId) {
        assets.setChapterLink(projectId, chapterId, assetId, userId(), true);
        return ApiResponse.success(null);
    }

    /** 解除本章引用，项目资产可继续复用。 */
    @DeleteMapping("/script/chapters/{chapterId}/assets/{assetId}")
    public ApiResponse<Void> detach(@PathVariable @Positive long projectId, @PathVariable @Positive long chapterId,
            @PathVariable @Positive long assetId) {
        assets.setChapterLink(projectId, chapterId, assetId, userId(), false);
        return ApiResponse.success(null);
    }

    /** 新增图片项并保存生成草稿。 */
    @PostMapping("/assets/{assetId}/visuals")
    public ApiResponse<Created> createVisual(@PathVariable @Positive long projectId, @PathVariable @Positive long assetId,
            @Valid @RequestBody SaveVisual request) {
        return ApiResponse.success(new Created(assets.saveVisual(projectId, assetId, null, userId(), request)));
    }

    /** 保存同一图片项的设置。 */
    @PutMapping("/assets/{assetId}/visuals/{visualId}")
    public ApiResponse<Created> editVisual(@PathVariable @Positive long projectId, @PathVariable @Positive long assetId,
            @PathVariable @Positive long visualId, @Valid @RequestBody SaveVisual request) {
        return ApiResponse.success(new Created(assets.saveVisual(projectId, assetId, visualId, userId(), request)));
    }

    /** 设定当前资产的基准图。 */
    @PutMapping("/assets/{assetId}/primary-visual/{visualId}")
    public ApiResponse<Void> primary(@PathVariable @Positive long projectId, @PathVariable @Positive long assetId,
            @PathVariable @Positive long visualId) {
        assets.setPrimary(projectId, assetId, visualId, userId());
        return ApiResponse.success(null);
    }

    /** 读取镜头当前参考图及采用检查信息。 */
    @GetMapping("/storyboard-shots/{shotId}/asset-image")
    public ApiResponse<StoryboardImageSnapshot> shotImage(@PathVariable @Positive long projectId, @PathVariable @Positive long shotId) {
        return ApiResponse.success(assets.shotImageContext(projectId, shotId, userId()));
    }

    /** 将章节资产的当前产物保存为镜头参考图。 */
    @PutMapping("/storyboard-shots/{shotId}/asset-image")
    public ApiResponse<Void> adopt(@PathVariable @Positive long projectId, @PathVariable @Positive long shotId,
            @Valid @RequestBody ShotAssetImage request) {
        assets.adoptForShot(projectId, shotId, userId(), request);
        return ApiResponse.success(null);
    }

    /** 返回新资源的标识，便于定位详情。 */
    public record Created(long id) {}

    private long userId() { return SecurityUtils.requireCurrentUserId(); }
}
