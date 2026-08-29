package com.manga.controller;

import com.manga.common.api.ApiResponse;
import com.manga.common.enums.ApplicationStatus;
import com.manga.config.properties.ApplicationProperties;
import com.manga.dto.SystemHealthResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 提供无需认证的系统基础信息接口。
 */
@RestController
@RequestMapping("/api/v1/public")
@Slf4j
@RequiredArgsConstructor
public class SystemController {

    private final ApplicationProperties applicationProperties;

    /**
     * 返回应用当前是否可正常提供服务。
     */
    @GetMapping("/health")
    public ApiResponse<SystemHealthResponse> health() {
        log.info("[SystemController#health] request");
        SystemHealthResponse result = new SystemHealthResponse(
                applicationProperties.name(),
                ApplicationStatus.UP
        );
        log.info(
                "[SystemController#health] response application={} status={}",
                result.application(),
                result.status()
        );
        return ApiResponse.success(result);
    }
}
