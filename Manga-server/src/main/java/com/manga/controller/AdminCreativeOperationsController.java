package com.manga.controller;

import com.manga.agent.operations.RuntimeOperationsConstants;
import com.manga.agent.operations.RuntimeOperationsService;
import com.manga.agent.operations.CreativeAuditArchiveService;
import com.manga.common.api.ApiResponse;
import com.manga.common.constant.SecurityExpressionConstants;
import com.manga.common.security.SecurityUtils;
import com.manga.agent.operations.RuntimeOperationsViews;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.List;

/** 管理员专用容量、用量追踪和审计归档接口；从不返回连接配置或凭据。 */
@RestController
@RequestMapping(RuntimeOperationsConstants.ADMIN_PATH)
@PreAuthorize(SecurityExpressionConstants.HAS_ADMIN_ROLE)
@Validated
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Creative Operations", description = "管理员运行监测、用量追踪与审计归档")
public class AdminCreativeOperationsController {
    private final RuntimeOperationsService operations;
    private final CreativeAuditArchiveService archives;
    /** 查询近 24 小时用量、Run 分布和有界供应商门禁摘要。 */
    @GetMapping
    @Operation(summary = "查询创作运行运维面板")
    public ApiResponse<RuntimeOperationsService.Snapshot> snapshot() {
        var result = operations.snapshot(); log.debug("Creative operations queried userId={}", SecurityUtils.requireCurrentUserId());
        return ApiResponse.success(result);
    }
    /** 根据 runId 和调用游标查找用量、失败类型与 Blueprint。 */
    @GetMapping("/runs/{runId}/usage")
    @Operation(summary = "管理员查询运行用量账本")
    public ApiResponse<List<RuntimeOperationsViews.Call>> usage(@PathVariable String runId, @RequestParam(required = false) @Positive Long beforeId) {
        return ApiResponse.success(operations.adminUsage(runId, beforeId));
    }
    /** 幂等生成归档，包含用户内容，禁止发送到日志。 */
    @PostMapping("/runs/{runId}/archive")
    @Operation(summary = "创建已完成运行的审计归档")
    public ApiResponse<RuntimeOperationsViews.Archive> archive(@PathVariable String runId) {
        var result = archives.archive(runId, Long.toString(SecurityUtils.requireCurrentUserId()));
        log.info("Creative audit archive created runId={} digest={}", runId, result.getSha256());
        return ApiResponse.success(RuntimeOperationsViews.Archive.from(result));
    }
    /** 导出前验证 SHA-256；这是审计副本，媒体和数据库需独立备份。 */
    @GetMapping("/runs/{runId}/archive")
    @Operation(summary = "校验并读取运行审计归档")
    public ApiResponse<RuntimeOperationsViews.Archive> export(@PathVariable String runId) {
        return ApiResponse.success(RuntimeOperationsViews.Archive.from(archives.export(runId)));
    }
}
