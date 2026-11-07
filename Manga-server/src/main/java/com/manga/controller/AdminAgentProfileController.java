package com.manga.controller;

import com.manga.agent.profile.AgentProfileApi;
import com.manga.agent.profile.AgentProfileContracts;
import com.manga.agent.profile.AgentProfileManagementService;
import com.manga.agent.profile.AgentProfileTestService;
import com.manga.agent.application.CreativeRunQueryService;
import com.manga.agent.api.CreativeRunResponse;
import com.manga.common.api.ApiResponse;
import com.manga.common.enums.AiProviderType;
import com.manga.common.constant.SecurityExpressionConstants;
import com.manga.common.constant.OpenApiConstants;
import com.manga.common.security.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import java.util.List;
import java.util.Set;

/** 管理员配置版本、发布、回滚、启停和只读测试；不接收或执行代码。 */
@RestController
@Validated
@RequiredArgsConstructor
@Slf4j
@RequestMapping(AgentProfileContracts.ADMIN_PATH)
@PreAuthorize(SecurityExpressionConstants.HAS_ADMIN_ROLE)
@SecurityRequirement(name = OpenApiConstants.BEARER_AUTH_SCHEME)
@Tag(name = "Admin Agent Profile", description = "专业 Agent 的版本与发布管理")
public class AdminAgentProfileController {
    private final AgentProfileManagementService profiles;
    private final AgentProfileTestService tests;
    private final CreativeRunQueryService runs;

    /** 有界返回身份和当前发布指针。 */
    @GetMapping
    @Operation(summary = "查询 Agent 配置列表")
    public ApiResponse<List<AgentProfileApi.Summary>> list() {
        var result = profiles.list(); log.debug("Admin Agent profiles userId={} count={}", SecurityUtils.requireCurrentUserId(), result.size());
        return ApiResponse.success(result);
    }

    /** 返回可配置的已注册能力及 schema 指纹。 */
    @GetMapping("/capabilities")
    @Operation(summary = "查询允许配置的能力", description = "只能选择已有业务能力，不能上传工具代码、类名或 URL。")
    public ApiResponse<List<AgentProfileApi.CapabilityOption>> capabilities() { return ApiResponse.success(profiles.capabilities()); }

    /** 返回已有对话模型协议，不包含用户配置或凭据。 */
    @GetMapping("/model-providers")
    @Operation(summary = "查询 Agent 可用模型协议")
    public ApiResponse<Set<AiProviderType>> modelProviders() {
        return ApiResponse.success(profiles.modelProviders());
    }

    /** 创建后处于未发布状态，不会被普通 Run 选择。 */
    @PostMapping
    @Operation(summary = "创建专业 Agent 草稿")
    public ApiResponse<AgentProfileApi.Detail> create(@Valid @RequestBody AgentProfileApi.Create request) {
        var result = profiles.create(request, SecurityUtils.requireCurrentUserId());
        log.info("Admin Agent created profileId={} revision={}", request.profileId(), result.profile().revision());
        return ApiResponse.success(result);
    }

    /** 获取身份和有界版本摘要。 */
    @GetMapping("/{key}")
    @Operation(summary = "查询 Agent 配置详情")
    public ApiResponse<AgentProfileApi.Detail> detail(@PathVariable @Pattern(regexp = AgentProfileContracts.KEY_PATTERN) String key) {
        return ApiResponse.success(profiles.detail(key));
    }

    /** 获取一个精确版本的完整纯数据定义。 */
    @GetMapping("/{key}/versions/{version}")
    @Operation(summary = "查询 Agent 定义版本")
    public ApiResponse<AgentProfileApi.Version> version(@PathVariable String key, @PathVariable @Positive long version) {
        return ApiResponse.success(profiles.version(key, version));
    }

    /** 从页面编辑内容创建新版本，发布指针不变。 */
    @PostMapping("/{key}/versions")
    @Operation(summary = "新增 Agent 草稿版本")
    public ApiResponse<AgentProfileApi.Detail> draft(@PathVariable String key, @Valid @RequestBody AgentProfileApi.Draft request) {
        return ApiResponse.success(profiles.createDraft(key, request, SecurityUtils.requireCurrentUserId()));
    }

    /** 修改尚未发布的版本，revision 冲突要求刷新。 */
    @PutMapping("/{key}/versions/{version}")
    @Operation(summary = "修改 Agent 草稿", description = "已发布内容不可修改，必须先新增草稿。")
    public ApiResponse<AgentProfileApi.Detail> edit(@PathVariable String key, @PathVariable @Positive long version,
            @Valid @RequestBody AgentProfileApi.Draft request) {
        return ApiResponse.success(profiles.editDraft(key, version, request, SecurityUtils.requireCurrentUserId()));
    }

    /** 发布时重新校验 schema、能力、权限、模型策略和预算。 */
    @PostMapping("/{key}/versions/{version}/publish")
    @Operation(summary = "发布 Agent 版本", description = "只改变后续运行的选择，已有 Blueprint 快照保持不变。")
    public ApiResponse<AgentProfileApi.Detail> publish(@PathVariable String key, @PathVariable @Positive long version,
            @Valid @RequestBody AgentProfileApi.Release request) {
        return ApiResponse.success(profiles.publish(key, version, request, SecurityUtils.requireCurrentUserId()));
    }

    /** 回滚到已发布历史版本，不重新创建内容。 */
    @PostMapping("/{key}/versions/{version}/rollback")
    @Operation(summary = "回滚 Agent 发布指针")
    public ApiResponse<AgentProfileApi.Detail> rollback(@PathVariable String key, @PathVariable @Positive long version,
            @Valid @RequestBody AgentProfileApi.Release request) {
        return ApiResponse.success(profiles.rollback(key, version, request, SecurityUtils.requireCurrentUserId()));
    }

    /** 禁用不取消已经开始的 Step。 */
    @PostMapping("/{key}/enabled")
    @Operation(summary = "启停 Agent 配置", description = "禁用后禁止新的选择，不撤销已开始运行的冻结执行规格。")
    public ApiResponse<AgentProfileApi.Detail> enable(@PathVariable String key, @Valid @RequestBody AgentProfileApi.Enable request) {
        var result = profiles.enable(key, request, SecurityUtils.requireCurrentUserId());
        log.info("Admin Agent enable changed profileId={} enabled={} revision={}", key, request.enabled(), result.profile().revision());
        return ApiResponse.success(result);
    }

    /** 读取追加式操作记录，支持稳定游标分页。 */
    @GetMapping("/{key}/audits")
    @Operation(summary = "查询 Agent 发布审计")
    public ApiResponse<List<AgentProfileApi.Audit>> audits(@PathVariable String key, @RequestParam(required = false) @Positive Long beforeId) {
        return ApiResponse.success(profiles.audits(key, beforeId));
    }

    /** 无模型调用地检查版本能否使用管理员的模型配置编译。 */
    @PostMapping("/{key}/versions/{version}/preview")
    @Operation(summary = "预览 Agent 测试规格", description = "只编译和校验，不调用模型、不创建 Run。")
    public ApiResponse<AgentProfileApi.Preview> preview(@PathVariable String key, @PathVariable @Positive long version,
            @Valid @RequestBody AgentProfileApi.Test request) {
        return ApiResponse.success(tests.preview(key, version, request, SecurityUtils.requireCurrentUserId()));
    }

    /** 显式启动一个只读、有限轮数的真实测试 Run。 */
    @PostMapping("/{key}/versions/{version}/test")
    @Operation(summary = "启动 Agent 只读测试", description = "会使用管理员自己的模型并产生模型调用费用；不允许业务提案、付费媒体生成或委派。")
    public ApiResponse<CreativeRunResponse> test(@PathVariable String key, @PathVariable @Positive long version,
            @Valid @RequestBody AgentProfileApi.Test request) {
        long userId = SecurityUtils.requireCurrentUserId(); var started = tests.start(key, version, request, userId);
        log.info("Admin Agent sandbox started profileId={} version={} runId={} userId={}", key, version, started.runId(), userId);
        return ApiResponse.success(runs.findOwned(started.runId(), userId));
    }
}
