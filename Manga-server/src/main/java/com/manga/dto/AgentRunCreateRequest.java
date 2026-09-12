package com.manga.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 创建一条助手运行的请求。 */
public record AgentRunCreateRequest(
        @Size(max = 36) String conversationId,
        Long projectId,
        @NotBlank @Size(max = 12000) String message
) {
}
