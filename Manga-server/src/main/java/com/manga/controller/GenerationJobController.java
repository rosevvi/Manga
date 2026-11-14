package com.manga.controller;

import com.manga.agent.generation.GenerationConstants;
import com.manga.agent.generation.GenerationJobQueryService;
import com.manga.agent.generation.GenerationJobService;
import com.manga.agent.generation.GenerationJobView;
import com.manga.agent.generation.VideoGenerationQuoteService;
import com.manga.agent.generation.ImageGenerationOption;
import com.manga.agent.generation.ImageGenerationQuoteService;
import com.manga.agent.generation.ArtifactApplicationService;
import com.manga.agent.generation.ArtifactAdoptionPreview;
import com.manga.agent.generation.ArtifactAdoptionResult;
import com.manga.common.api.ApiResponse;
import com.manga.common.enums.AiModelType;
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
import org.springframework.http.ResponseEntity;
import org.springframework.http.CacheControl;

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
    private final VideoGenerationQuoteService videoQuotes;
    private final ArtifactApplicationService artifacts;

    /** 获取经过所有权校验的新下载链接，禁止代理缓存签名。 */
    @GetMapping("/{jobId}/download")
    @Operation(summary = "下载生成媒体", description = "只为当前用户已转存产物生成新下载链接，OSS 响应带 attachment。")
    public ResponseEntity<ApiResponse<GenerationJobQueryService.Download>> download(@PathVariable @Size(max = 36) String jobId) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(ApiResponse.success(query.download(SecurityUtils.requireCurrentUserId(), jobId)));
    }
    /** 采用前先展示目标当前图片，并取得新的乐观版本。 */
    @GetMapping("/{jobId}/adoption-preview")
    @Operation(summary = "预览采用生成图片", description = "返回当前镜头图片、生成产物及项目 revision；不会修改镜头。")
    public ApiResponse<ArtifactAdoptionPreview> adoptionPreview(@PathVariable @Size(max = 36) String jobId) {
        long userId = SecurityUtils.requireCurrentUserId(); ArtifactAdoptionPreview result = artifacts.preview(jobId, userId);
        log.debug("Artifact adoption preview userId={} jobId={} artifactId={}", userId, jobId, result.artifactId());
        return ApiResponse.success(result);
    }
    /** 只接收预览版本，产物地址、目标镜头和采用人都由服务端确定。 */
    @PostMapping("/{jobId}/adopt")
    @Operation(summary = "采用产物为镜头图片", description = "校验预览 revision，只更新镜头图片与来源；并发编辑返回冲突，重复请求幂等。")
    public ApiResponse<ArtifactAdoptionResult> adopt(@PathVariable @Size(max = 36) String jobId, @Valid @RequestBody AdoptionRequest request) {
        long userId = SecurityUtils.requireCurrentUserId();
        ArtifactAdoptionResult result = artifacts.adopt(jobId, userId, request.expectedProjectRevision());
        log.info("Artifact adopted userId={} jobId={} artifactId={} revision={}", userId, jobId, result.artifactId(), result.projectRevision());
        return ApiResponse.success(result);
    }
    /** 采用确认只声明用户预览过的版本，不允许提交任意媒体地址。 */
    public record AdoptionRequest(/** 预览响应中的项目版本。 */ @Positive long expectedProjectRevision) { }
    /** 图片选项只返回最小摘要，不复用含密钥的配置管理响应。 */
    @GetMapping("/options")
    @Operation(summary = "查询可选图片模型配置", description = "返回当前用户配置、固定模型和预计费用；费用为空表示尚未核价、不能生成。")
    public ApiResponse<List<ImageGenerationOption>> options() {
        long userId = SecurityUtils.requireCurrentUserId(); List<ImageGenerationOption> result = quotes.options(userId);
        log.debug("Generation options userId={} count={}", userId, result.size());
        return ApiResponse.success(result);
    }
    /** 查询当前用户可用的视频模型，默认项排在最前。 */
    @GetMapping("/video-options")
    @Operation(summary = "查询可选视频模型配置", description = "仅返回启用且已保存凭据的 Agnes 视频配置和 720P 每秒预估价格。")
    public ApiResponse<List<ImageGenerationOption>> videoOptions() {
        return ApiResponse.success(videoQuotes.options(SecurityUtils.requireCurrentUserId()));
    }
    /** 从数据库恢复项目或指定运行下的生成卡片。 */
    @GetMapping
    @Operation(summary = "查询媒体作业", description = "返回图片或视频作业状态和真实进度，支持游标分页。")
    public ApiResponse<List<GenerationJobView>> list(@RequestParam @Positive long projectId,
            @RequestParam(required = false) @Size(max = 36) String runId,
            @RequestParam(required = false) @Size(max = 36) String conversationId,
            @RequestParam(required = false) @Positive Long beforeId,
            @RequestParam(required = false) @Positive Long shotId,
            @RequestParam(required = false) AiModelType mediaType) {
        long userId = SecurityUtils.requireCurrentUserId();
        List<GenerationJobView> result = query.list(userId, projectId, runId, conversationId, beforeId, shotId, mediaType);
        log.debug("Generation list userId={} projectId={} runId={} count={}", userId, projectId, runId, result.size());
        return ApiResponse.success(result);
    }
    /** 单项快照不返回供应商原始响应、密钥和图片 Base64。 */
    @GetMapping("/{jobId}")
    @Operation(summary = "查询媒体作业详情", description = "用于 SSE 重连和刷新恢复，产物链接仅在转存完成后提供。")
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
