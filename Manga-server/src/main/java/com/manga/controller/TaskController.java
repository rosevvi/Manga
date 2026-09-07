package com.manga.controller;

import com.manga.common.api.ApiResponse;
import com.manga.common.security.SecurityUtils;
import com.manga.dto.TaskResponse;
import com.manga.service.ScriptTaskService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 提供可恢复后台任务的查询接口。 */
@RestController
@RequestMapping("/api/v1/tasks")
@RequiredArgsConstructor
public class TaskController {

    private final ScriptTaskService scriptTaskService;

    /** 查询当前用户的活动任务。 */
    @GetMapping("/active")
    public ApiResponse<List<TaskResponse>> active() {
        return ApiResponse.success(scriptTaskService.list(SecurityUtils.requireCurrentUserId(), true));
    }

    /** 查询当前用户任务历史。 */
    @GetMapping
    public ApiResponse<List<TaskResponse>> list() {
        return ApiResponse.success(scriptTaskService.list(SecurityUtils.requireCurrentUserId(), false));
    }

    /** 查询任务详情。 */
    @GetMapping("/{taskId}")
    public ApiResponse<TaskResponse> find(@PathVariable long taskId) {
        return ApiResponse.success(scriptTaskService.toResponseForUser(taskId, SecurityUtils.requireCurrentUserId()));
    }
}
