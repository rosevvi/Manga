package com.manga.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 创建一条助手运行的请求。 */
public record AgentRunCreateRequest(
        /** 可选的既有助手会话标识。 */
        @Size(max = 36) String conversationId,
        /** 可选的绑定项目主键。 */
        Long projectId,
        /** 用户提交的助手指令。 */
        @NotBlank @Size(max = 12000) String message
) {
}
