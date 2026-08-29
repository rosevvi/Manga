package com.manga.controller;

import com.manga.common.api.ApiResponse;
import com.manga.common.constant.SecurityExpressionConstants;
import com.manga.dto.RoleResponse;
import com.manga.service.RoleService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 提供管理员角色目录查询接口。
 */
@RestController
@RequestMapping("/api/v1/roles")
@PreAuthorize(SecurityExpressionConstants.HAS_ADMIN_ROLE)
@Slf4j
@RequiredArgsConstructor
public class RoleController {

    private final RoleService roleService;

    /**
     * 返回全部系统角色定义。
     */
    @GetMapping
    public ApiResponse<List<RoleResponse>> findAll() {
        log.info("[RoleController#findAll] request");
        List<RoleResponse> result = roleService.findAll();
        log.info("[RoleController#findAll] response count={}", result.size());
        return ApiResponse.success(result);
    }
}
