package com.manga.agent.legacy;

import com.manga.agent.MangaAgentRuntimeService;
import com.manga.agent.application.CancelCreativeRunCommand;
import com.manga.agent.application.CreativeRunStartResult;
import com.manga.agent.application.CreativeRuntime;
import com.manga.agent.application.StartCreativeRunCommand;
import com.manga.agent.domain.CreativeRuntimeType;
import com.manga.agent.domain.CreativeOperationType;
import com.manga.agent.domain.CreativeRunTriggerSource;
import com.manga.agent.api.AgentRuntimeResponseCode;
import com.manga.common.exception.BusinessException;
import com.manga.dto.AgentRunCreateRequest;
import com.manga.dto.AgentRunResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/**
 * 将现有 AgentScope 执行链适配为统一创作运行时。
 */
@Component
@RequiredArgsConstructor
public class LegacyCreativeRuntime implements CreativeRuntime {

    private final MangaAgentRuntimeService legacyRuntimeService;

    @Override
    public CreativeRuntimeType runtimeType() {
        return CreativeRuntimeType.LEGACY;
    }

    @Override
    public CreativeRunStartResult start(StartCreativeRunCommand command) {
        if (command.triggerSource() != CreativeRunTriggerSource.CONVERSATION
                || command.operationType() != CreativeOperationType.ASSISTANT_CHAT) {
            throw new BusinessException(AgentRuntimeResponseCode.LEGACY_RUNTIME_UNSUPPORTED_OPERATION,
                    HttpStatus.UNPROCESSABLE_ENTITY);
        }
        AgentRunResponse response = legacyRuntimeService.start(command.userId(), new AgentRunCreateRequest(
                command.conversationId(), command.projectId(), command.instruction()));
        return new CreativeRunStartResult(response.runId(), response.conversationId(), response.projectId(),
                response.status(), response.startedAt(), response.finishedAt(), response.errorCode(),
                response.errorMessage());
    }

    @Override
    public void cancel(CancelCreativeRunCommand command) {
        legacyRuntimeService.cancel(command.runId(), command.userId());
    }
}
