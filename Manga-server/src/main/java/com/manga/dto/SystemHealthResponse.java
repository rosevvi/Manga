package com.manga.dto;

import com.manga.common.enums.ApplicationStatus;

/**
 * 返回应用名称和当前运行状态。
 */
public record SystemHealthResponse(String application, ApplicationStatus status) {
}
