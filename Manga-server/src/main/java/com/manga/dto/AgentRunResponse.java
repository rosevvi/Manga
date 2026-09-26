package com.manga.dto;

import com.manga.agent.AgentRunStatus;

import java.time.LocalDateTime;

/** 助手运行摘要。 */
public record AgentRunResponse(
        /** Run 唯一标识。 */
        String runId,
        /** 可选的所属助手会话标识。 */
        String conversationId,
        /** 可选的绑定项目主键。 */
        Long projectId,
        /** Run 当前状态。 */
        AgentRunStatus status,
        /** Run 开始时间。 */
        LocalDateTime startedAt,
        /** Run 结束时间，未结束时为空。 */
        LocalDateTime finishedAt,
        /** 终态错误编码，未失败时为空。 */
        String errorCode,
        /** 脱敏后的终态错误摘要，未失败时为空。 */
        String errorMessage
) {
}
