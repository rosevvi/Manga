package com.manga.controller;

import com.manga.agent.generation.GenerationConstants;
import com.manga.agent.generation.GenerationJobQueryService;
import com.manga.agent.generation.GenerationJobService;
import com.manga.agent.generation.GenerationJobView;
import com.manga.agent.generation.ImageGenerationOption;
import com.manga.agent.generation.ImageGenerationQuoteService;
import com.manga.common.api.ApiResponse;
import com.manga.common.constant.OpenApiConstants;
import com.manga.common.security.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import java.util.List;

/** 图片作业监测与人工核对接口，付费提交只能通过 ChangeSet 审批创建。 */
@RestController
@RequestMapping(GenerationConstants.API)
@RequiredArgsConstructor
@Validated
@Slf4j
@Tag(name = "Generation Job", description = "外部图片作业进度、平台产物和未知提交核对")
@SecurityRequirement(name = OpenApiConstants.BEARER_AUTH_SCHEME)
public class GenerationJobController {
    private final GenerationJobQueryService query;
    private final GenerationJobService lifecycle;
    private final ImageGenerationQuoteService quotes;
    /** 图片选项只返回最小摘要，不复用含密钥的配置管理响应。 */
    @GetMapping("/options")
    @Operation(summary = "查询可选图片模型配置", description = "返回当前用户配置、固定模型和预计费用；费用为空表示尚未核价、不能生成。")
    public ApiResponse<List<ImageGenerationOption>> options() {
        long userId = SecurityUtils.requireCurrentUserId(); List<ImageGenerationOption> result = quotes.options(userId);
        log.debug("Generation options userId={} count={}", userId, result.size());
        return ApiResponse.success(result);
    }
    /** 从数据库恢复项目或指定运行下的生成卡片。 */
    @GetMapping
    @Operation(summary = "查询图片作业", description = "返回真实阶段和可空百分比；beforeId 用于每页 25 条历史查询。")
    public ApiResponse<List<GenerationJobView>> list(@RequestParam @Positive long projectId,
            @RequestParam(required = false) @Size(max = 36) String runId,
            @RequestParam(required = false) @Size(max = 36) String conversationId,
            @RequestParam(required = false) @Positive Long beforeId) {
        long userId = SecurityUtils.requireCurrentUserId();
        List<GenerationJobView> result = query.list(userId, projectId, runId, conversationId, beforeId);
        log.debug("Generation list userId={} projectId={} runId={} count={}", userId, projectId, runId, result.size());
        return ApiResponse.success(result);
    }
    /** 单项快照不返回供应商原始响应、密钥和图片 Base64。 */
    @GetMapping("/{jobId}")
    @Operation(summary = "查询图片作业详情", description = "用于 SSE 重连和刷新恢复，产物链接仅在转存完成后提供。")
    public ApiResponse<GenerationJobView> find(@PathVariable @Size(max = 36) String jobId) {
        long userId = SecurityUtils.requireCurrentUserId(); GenerationJobView result = query.find(userId, jobId);
        log.debug("Generation snapshot userId={} jobId={} status={}", userId, jobId, result.status());
        return ApiResponse.success(result);
    }
    /** 取消仅撤销采用权，同步供应商可能仍继续执行和计费。 */
    @PostMapping("/{jobId}/cancel")
    @Operation(summary = "取消图片结果采用", description = "未提交则不调用供应商；已提交不承诺供应商停止计费，晚到结果不得发布。")
    public ApiResponse<GenerationJobView> cancel(@PathVariable @Size(max = 36) String jobId) {
        long userId = SecurityUtils.requireCurrentUserId(); lifecycle.cancel(jobId, userId);
        log.info("Generation cancel userId={} jobId={}", userId, jobId);
        return ApiResponse.success(query.find(userId, jobId));
    }
    /** 关闭未知提交之前要求用户明确核对供应商账单和结果。 */
    @PostMapping("/{jobId}/acknowledge-unknown")
    @Operation(summary = "确认已核对未知提交", description = "只结束平台等待并释放风险容量，不重提生成，不表示远程取消或退款。")
    public ApiResponse<GenerationJobView> acknowledge(@PathVariable @Size(max = 36) String jobId, @Valid @RequestBody ReconciliationRequest request) {
        long userId = SecurityUtils.requireCurrentUserId(); lifecycle.acknowledgeUnknown(jobId, userId, request.note());
        log.info("Generation unknown acknowledged userId={} jobId={}", userId, jobId);
        return ApiResponse.success(query.find(userId, jobId));
    }
    /** 明确核对操作及说明，不能通过普通重试按钮隐式完成。 */
    public record ReconciliationRequest(
            /** 用户确认已经自行核对供应商事实。 */ @AssertTrue boolean checkedSupplier,
            /** 核对摘要，禁止填入密钥和完整供应商响应。 */ @NotBlank @Size(max = 2000) String note
    ) { }
}
