package com.manga.controller;

import com.manga.agent.profile.AgentProfileApi;
import com.manga.agent.profile.AgentProfileCatalogService;
import com.manga.agent.profile.AgentProfileContracts;
import com.manga.common.api.ApiResponse;
import com.manga.common.security.SecurityUtils;
import com.manga.common.constant.OpenApiConstants;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.List;

/** 登录用户可选择的已发布专业 Agent 目录，不提供管理定义内容。 */
@RestController
@RequiredArgsConstructor
@Slf4j
@RequestMapping(AgentProfileContracts.CATALOG_PATH)
@SecurityRequirement(name = OpenApiConstants.BEARER_AUTH_SCHEME)
@Tag(name = "Agent Catalog", description = "当前启用的发布 Agent 目录")
public class AgentProfileController {
    private final AgentProfileCatalogService catalog;

    /** 返回当前发布状态，禁用和回滚后下次请求即生效。 */
    @GetMapping
    @Operation(summary = "查询专业 Agent 目录", description = "要求正式登录，只返回名称、职责、角色、版本和权限，不返回 Prompt 或模型凭据。")
    public ApiResponse<List<AgentProfileApi.CatalogItem>> list() {
        long userId = SecurityUtils.requireCurrentUserId(); var items = catalog.list();
        log.debug("Agent catalog userId={} count={}", userId, items.size());
        return ApiResponse.success(items);
    }
}
