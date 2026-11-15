package com.manga.controller;

import com.manga.agent.api.CreativeApiConstants;
import com.manga.agent.api.RepairDecisionRequest;
import com.manga.agent.api.RepairDecisionView;
import com.manga.agent.collaboration.CreativeRepairDecisionService;
import com.manga.common.api.ApiResponse;
import com.manga.common.security.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import com.manga.common.constant.OpenApiConstants;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.List;

/** 所有者查询与确认修订的接口，不执行 ChangeSet 批准或业务写入。 */
@RestController
@RequiredArgsConstructor
@Slf4j
@RequestMapping(CreativeApiConstants.CREATIVE_RUNS_PATH + "/{runId}/repair-decisions")
@Tag(name = "Creative Repair", description = "评审问题预览与用户授权修订")
@SecurityRequirement(name = OpenApiConstants.BEARER_AUTH_SCHEME)
public class CreativeRepairDecisionController {
    private final CreativeRepairDecisionService service;
    /** 查询当前用户任务的确认历史。 */
    @GetMapping
    @Operation(summary = "查看修订确认与候选", description = "返回评审问题和未批准草稿，校验 Run 所有权。")
    public ApiResponse<List<RepairDecisionView>> list(@PathVariable String runId) {
        var result = service.list(runId, SecurityUtils.requireCurrentUserId());
        log.debug("Creative repair history runId={} count={}", runId, result.size());
        return ApiResponse.success(result);
    }
    /** 幂等授权下一轮修订或结束任务。 */
    @PostMapping("/{decisionId}/decide")
    @Operation(summary = "确认修订或结束任务", description = "只授权生成下一轮候选，最终保存仍须 ChangeSet 审批。")
    public ApiResponse<RepairDecisionView> decide(@PathVariable String runId, @PathVariable String decisionId,
            @Valid @RequestBody RepairDecisionRequest request) {
        long userId = SecurityUtils.requireCurrentUserId();
        log.info("Creative repair requested userId={} runId={} decisionId={} action={}", userId, runId, decisionId, request.action());
        return ApiResponse.success(service.decide(runId, decisionId, userId, request));
    }
}
