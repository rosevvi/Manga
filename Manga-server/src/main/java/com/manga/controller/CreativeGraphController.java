package com.manga.controller;

import com.manga.agent.api.CreativeApiConstants;
import com.manga.agent.collaboration.CreativeGraphQueryService;
import com.manga.agent.collaboration.CreativeGraphView;
import com.manga.common.api.ApiResponse;
import com.manga.common.constant.OpenApiConstants;
import com.manga.common.security.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 多 Agent 计划、步骤依赖和协作预算的用户接口。 */
@RestController
@RequiredArgsConstructor
@RequestMapping(CreativeApiConstants.CREATIVE_RUNS_PATH)
@Tag(name = "Creative Collaboration", description = "查询可恢复的协作步骤图并停止步骤")
@SecurityRequirement(name = OpenApiConstants.BEARER_AUTH_SCHEME)
public class CreativeGraphController {
    private final CreativeGraphQueryService graphs;

    /** 刷新页面后从数据库恢复协作计划、当前 Agent、等待原因和结果摘要。 */
    @GetMapping("/{runId}/graph")
    @Operation(summary = "查询创作协作图", description = "仅返回当前用户拥有的 Run；包含步骤树、ALL_SUCCESS 依赖、汇合状态及冻结预算。")
    public ApiResponse<CreativeGraphView> find(@PathVariable String runId) {
        return ApiResponse.success(graphs.find(runId, SecurityUtils.requireCurrentUserId()));
    }

    /** 步骤停止遵循本 Run 的失败策略，并非后台直接删除节点。 */
    @PostMapping("/{runId}/steps/{stepId}/cancel")
    @Operation(summary = "停止协作步骤", description = "首版使用 ALL_SUCCESS 和 FAIL_RUN，停止任意未完成步骤会取消整个 Run 及其子步骤。")
    public ApiResponse<Void> cancel(@PathVariable String runId, @PathVariable String stepId) {
        graphs.cancelStep(runId, stepId, SecurityUtils.requireCurrentUserId());
        return ApiResponse.success(null);
    }
}
