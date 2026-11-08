package com.manga.controller;

import com.manga.agent.operations.RuntimeOperationsConstants;
import com.manga.agent.operations.RuntimeOperationsService;
import com.manga.common.api.ApiResponse;
import com.manga.common.security.SecurityUtils;
import com.manga.agent.operations.RuntimeOperationsViews;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import java.util.List;

/** 当前用户 Run 的用量诊断接口，先查所有权再返回账本。 */
@RestController
@Validated
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Creative Usage", description = "创作 Run 的模型调用用量与费用诊断")
public class CreativeOperationsController {
    private final RuntimeOperationsService operations;
    /** 有界游标分页；空费用表示未知或未定价，不能视作免费。 */
    @GetMapping(RuntimeOperationsConstants.USER_PATH)
    @Operation(summary = "查询本人创作运行的模型用量")
    public ApiResponse<List<RuntimeOperationsViews.Call>> usage(@PathVariable String runId, @RequestParam(required = false) @Positive Long beforeId) {
        var result = operations.ownedUsage(runId, SecurityUtils.requireCurrentUserId(), beforeId);
        log.debug("Creative usage queried runId={} count={}", runId, result.size());
        return ApiResponse.success(result);
    }
}
