package com.manga.controller;

import com.manga.agent.changeset.ApprovalRequest;
import com.manga.agent.changeset.ApprovalResult;
import com.manga.agent.changeset.ChangeSetApprovalService;
import com.manga.agent.changeset.ChangeSetConstants;
import com.manga.agent.changeset.ChangeSetQueryService;
import com.manga.agent.changeset.ChangeSetView;
import com.manga.common.api.ApiResponse;
import com.manga.common.constant.OpenApiConstants;
import com.manga.common.security.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
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

/** AI 提案的差异预览和人工审批接口，不允许客户端上传已批准内容。 */
@RestController
@RequestMapping(ChangeSetConstants.API_PATH)
@RequiredArgsConstructor
@Validated
@Slf4j
@Tag(name = "Creative ChangeSet", description = "业务提案预览、审批、冲突及审计")
@SecurityRequirement(name = OpenApiConstants.BEARER_AUTH_SCHEME)
public class CreativeChangeSetController {
    private final ChangeSetQueryService query;
    private final ChangeSetApprovalService approvals;

    /** 分页查询一个运行或一个会话内的已开放提案。 */
    @GetMapping
    @Operation(summary = "查询创作提案", description = "runId 和 conversationId 必须且只能提供一个；按 beforeId 分页。")
    public ApiResponse<List<ChangeSetView>> list(
            @RequestParam(required = false) @Size(max = 36) String runId,
            @RequestParam(required = false) @Size(max = 36) String conversationId,
            @RequestParam(required = false) @Positive Long beforeId) {
        long userId = SecurityUtils.requireCurrentUserId();
        List<ChangeSetView> result = query.list(userId, runId, conversationId, beforeId);
        log.debug("ChangeSet list userId={} runId={} conversationId={} count={}", userId, runId, conversationId, result.size());
        return ApiResponse.success(result);
    }

    /** 读取一个不可变提案与审批记录。 */
    @GetMapping("/{changeSetId}")
    @Operation(summary = "查看提案差异", description = "返回生成前原文、冻结提案、基础版本与审批人。")
    public ApiResponse<ChangeSetView> find(@PathVariable String changeSetId) {
        long userId = SecurityUtils.requireCurrentUserId();
        ChangeSetView result = query.find(userId, changeSetId);
        log.debug("ChangeSet preview userId={} changeSetId={} status={}", userId, changeSetId, result.status());
        return ApiResponse.success(result);
    }

    /** 批准或拒绝冻结提案，冲突以结构化审批结果返回。 */
    @PostMapping("/{changeSetId}/decision")
    @Operation(summary = "批准或拒绝提案", description = "批准时校验 revision 并原子写入；重复同一决定幂等，冲突需重新生成。")
    public ApiResponse<ApprovalResult> decide(@PathVariable String changeSetId, @Valid @RequestBody ApprovalRequest request) {
        long userId = SecurityUtils.requireCurrentUserId();
        log.info("ChangeSet decision requested userId={} changeSetId={} decision={}", userId, changeSetId, request.decision());
        ApprovalResult result = approvals.decide(changeSetId, userId, request.decision(), request.comment());
        log.info("ChangeSet decision returned userId={} changeSetId={} status={}", userId, changeSetId, result.status());
        return ApiResponse.success(result);
    }
}
