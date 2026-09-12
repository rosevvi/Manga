package com.manga.dto;

import com.manga.agent.AgentRunStatus;

import java.time.LocalDateTime;

/** 助手运行摘要。 */
public record AgentRunResponse(
        String runId,
        String conversationId,
        Long projectId,
        AgentRunStatus status,
        LocalDateTime startedAt,
        LocalDateTime finishedAt,
        String errorCode,
        String errorMessage
) {
}
