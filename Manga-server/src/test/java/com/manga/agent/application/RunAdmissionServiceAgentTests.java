package com.manga.agent.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.manga.agent.blueprint.AgentBlueprint;
import com.manga.agent.blueprint.AgentBlueprintCompiler;
import com.manga.agent.blueprint.AgentKernelKey;
import com.manga.agent.blueprint.CompiledAgentBlueprint;
import com.manga.agent.config.CreativeRuntimeProperties;
import com.manga.agent.domain.AgentPermissionPolicy;
import com.manga.agent.domain.CreativeOperationType;
import com.manga.agent.domain.CreativeResourceReference;
import com.manga.agent.domain.CreativeResourceType;
import com.manga.agent.domain.CreativeStepType;
import com.manga.agent.execution.CreativeStepDispatcher;
import com.manga.agent.profile.AgentProfileKey;
import com.manga.entity.CreativeStep;
import com.manga.entity.ProjectScript;
import com.manga.repository.CreativeConversationRepository;
import com.manga.repository.CreativeDispatchOutboxRepository;
import com.manga.repository.CreativeRunRepository;
import com.manga.repository.CreativeStepRepository;
import com.manga.repository.ProjectScriptRepository;
import com.manga.service.ProjectService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 验证真实 Agent 操作在受理事务内冻结 Blueprint 与状态会话，而不是执行时临时拼装。
 */
class RunAdmissionServiceAgentTests {

    @Test
    void shouldCreateAgentInvocationStepForScriptParseOperation() {
        CreativeConversationRepository conversationRepository = mock(CreativeConversationRepository.class);
        CreativeRunRepository runRepository = mock(CreativeRunRepository.class);
        CreativeStepRepository stepRepository = mock(CreativeStepRepository.class);
        CreativeDispatchOutboxRepository outboxRepository = mock(CreativeDispatchOutboxRepository.class);
        CreativeEventService eventService = mock(CreativeEventService.class);
        ProjectService projectService = mock(ProjectService.class);
        CreativeIdentifierGenerator identifierGenerator = mock(CreativeIdentifierGenerator.class);
        CreativeStepDispatcher dispatcher = mock(CreativeStepDispatcher.class);
        AgentBlueprintCompiler blueprintCompiler = mock(AgentBlueprintCompiler.class);
        ProjectScriptRepository scriptRepository = mock(ProjectScriptRepository.class);
        ObjectMapper objectMapper = new ObjectMapper();
        when(runRepository.findByRequestId(11L, "request-1")).thenReturn(Optional.empty());
        when(scriptRepository.findByProjectId(21L)).thenReturn(Optional.of(ProjectScript.builder()
                .id(31L)
                .projectId(21L)
                .build()));
        when(identifierGenerator.nextRunId()).thenReturn("run-1");
        when(identifierGenerator.nextStepId()).thenReturn("step-1");
        when(identifierGenerator.nextStateSessionId()).thenReturn("state-session-1");
        when(blueprintCompiler.compile(any())).thenReturn(new CompiledAgentBlueprint(
                blueprint(), "{\"profileKey\":\"SCRIPT_PARSER\"}"));
        RunAdmissionService service = new RunAdmissionService(
                conversationRepository,
                runRepository,
                stepRepository,
                outboxRepository,
                eventService,
                projectService,
                new CreativeRuntimeProperties("engine-v2", "workflow-v2", 1, Duration.ofMinutes(30)),
                identifierGenerator,
                dispatcher,
                blueprintCompiler,
                scriptRepository,
                objectMapper);
        StartCreativeRunCommand command = StartCreativeRunCommand.manual(
                11L,
                "request-1",
                21L,
                CreativeOperationType.PARSE_SCRIPT,
                new CreativeResourceReference(CreativeResourceType.SCRIPT, 31L),
                AgentPermissionPolicy.READ_ONLY,
                "解析剧本结构与连续性");

        CreativeRunStartResult result = service.admit(command);

        ArgumentCaptor<CreativeStep> step = ArgumentCaptor.forClass(CreativeStep.class);
        verify(stepRepository).create(step.capture());
        assertThat(result.runId()).isEqualTo("run-1");
        assertThat(step.getValue().getStepType()).isEqualTo(CreativeStepType.AGENT_INVOCATION);
        assertThat(step.getValue().getBlueprintSnapshotJson()).contains("SCRIPT_PARSER");
        assertThat(step.getValue().getBlueprintFingerprint()).isEqualTo("fingerprint-1");
        assertThat(step.getValue().getStateSessionId()).isEqualTo("state-session-1");
        assertThat(step.getValue().getInputJson()).contains("PARSE_SCRIPT", "解析剧本结构与连续性");
        verify(projectService).requireAccessibleProject(21L, 11L);
        verify(dispatcher).dispatchAsync("step-1", 1L);
        verify(blueprintCompiler).compile(any());
    }

    /** 构造测试用剧本解析 Blueprint。 */
    private AgentBlueprint blueprint() {
        return new AgentBlueprint(
                new AgentKernelKey("kernel-1"),
                AgentProfileKey.SCRIPT_PARSER,
                1L,
                null,
                "只读解析剧本",
                Map.of(),
                List.of(),
                AgentPermissionPolicy.READ_ONLY,
                12,
                "fingerprint-1");
    }
}
