package com.manga.agent.durable;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.manga.agent.api.CreativeRuntimeResponseCode;
import com.manga.agent.application.CancelCreativeRunCommand;
import com.manga.agent.application.CreativeEventService;
import com.manga.agent.application.CreativeIdentifierGenerator;
import com.manga.agent.application.CreativeRunStartResult;
import com.manga.agent.application.StartCreativeRunCommand;
import com.manga.agent.config.CreativeRuntimeProperties;
import com.manga.agent.domain.AgentPermissionPolicy;
import com.manga.agent.domain.CreativeEventType;
import com.manga.agent.domain.CreativeOperationType;
import com.manga.agent.domain.CreativeResourceReference;
import com.manga.agent.domain.CreativeResourceType;
import com.manga.agent.domain.CreativeRunStatus;
import com.manga.agent.domain.CreativeWorkflowType;
import com.manga.common.exception.BusinessException;
import com.manga.entity.CreativeConversation;
import com.manga.entity.CreativeRun;
import com.manga.repository.CreativeConversationRepository;
import com.manga.repository.CreativeRunRepository;
import com.manga.service.ProjectService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Duration;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 验证单一 Durable Runtime 的受理、幂等、手动触发和取消边界。
 */
class DurableCreativeRuntimeTests {

    private static final long USER_ID = 101L;
    private static final long PROJECT_ID = 202L;
    private static final String RUN_ID = "run-test";
    private static final String CONVERSATION_ID = "conversation-test";
    private static final String REQUEST_ID = "request-test";

    private CreativeConversationRepository conversationRepository;
    private CreativeRunRepository runRepository;
    private CreativeEventService eventService;
    private ProjectService projectService;
    private CreativeIdentifierGenerator identifierGenerator;
    private DurableCreativeRuntime runtime;

    @BeforeEach
    void setUp() {
        conversationRepository = mock(CreativeConversationRepository.class);
        runRepository = mock(CreativeRunRepository.class);
        eventService = mock(CreativeEventService.class);
        projectService = mock(ProjectService.class);
        identifierGenerator = mock(CreativeIdentifierGenerator.class);
        CreativeRuntimeProperties properties = new CreativeRuntimeProperties(
                "durable-v1", "v1", 1, Duration.ofMinutes(30));
        runtime = new DurableCreativeRuntime(conversationRepository, runRepository, eventService,
                projectService, properties, identifierGenerator, new ObjectMapper());
        when(identifierGenerator.nextRunId()).thenReturn(RUN_ID);
        when(identifierGenerator.nextConversationId()).thenReturn(CONVERSATION_ID);
        when(conversationRepository.create(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(runRepository.create(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void shouldAcceptConversationRunWithoutCallingModel() {
        StartCreativeRunCommand command = StartCreativeRunCommand.conversation(
                USER_ID, REQUEST_ID, null, PROJECT_ID, "分析项目结构");

        CreativeRunStartResult result = runtime.start(command);

        ArgumentCaptor<CreativeRun> runCaptor = ArgumentCaptor.forClass(CreativeRun.class);
        verify(runRepository).create(runCaptor.capture());
        CreativeRun run = runCaptor.getValue();
        assertThat(result.status()).isEqualTo(CreativeRunStatus.QUEUED);
        assertThat(result.conversationId()).isEqualTo(CONVERSATION_ID);
        assertThat(run.getRequestFingerprint()).hasSize(64);
        assertThat(run.getWorkflowType()).isEqualTo(CreativeWorkflowType.ASSISTANT_CHAT);
        assertThat(run.getActiveConversationId()).isEqualTo(CONVERSATION_ID);
        verify(projectService).requireAccessibleProject(PROJECT_ID, USER_ID);
        verify(eventService).append(eq(RUN_ID), eq(CreativeEventType.RUN_ACCEPTED), any());
    }

    @Test
    void shouldAcceptManualChapterStoryboardRunWithoutConversation() {
        StartCreativeRunCommand command = StartCreativeRunCommand.manual(
                USER_ID, REQUEST_ID, PROJECT_ID, CreativeOperationType.GENERATE_CHAPTER_STORYBOARD,
                new CreativeResourceReference(CreativeResourceType.CHAPTER, 303L),
                AgentPermissionPolicy.PROPOSE_CHANGES, "生成本章分镜");

        runtime.start(command);

        ArgumentCaptor<CreativeRun> runCaptor = ArgumentCaptor.forClass(CreativeRun.class);
        verify(runRepository).create(runCaptor.capture());
        CreativeRun run = runCaptor.getValue();
        assertThat(run.getConversationId()).isNull();
        assertThat(run.getTargetType()).isEqualTo(CreativeResourceType.CHAPTER);
        assertThat(run.getTargetId()).isEqualTo(303L);
        assertThat(run.getWorkflowType()).isEqualTo(CreativeWorkflowType.CHAPTER_STORYBOARD);
        verify(conversationRepository, never()).create(any());
    }

    @Test
    void shouldReturnExistingRunForSameIdempotentRequest() {
        StartCreativeRunCommand command = StartCreativeRunCommand.manual(
                USER_ID, REQUEST_ID, PROJECT_ID, CreativeOperationType.GENERATE_CHAPTER_STORYBOARD,
                new CreativeResourceReference(CreativeResourceType.CHAPTER, 303L),
                AgentPermissionPolicy.PROPOSE_CHANGES, "生成本章分镜");
        runtime.start(command);
        ArgumentCaptor<CreativeRun> runCaptor = ArgumentCaptor.forClass(CreativeRun.class);
        verify(runRepository).create(runCaptor.capture());
        when(runRepository.findByRequestId(USER_ID, REQUEST_ID))
                .thenReturn(Optional.of(runCaptor.getValue()));

        CreativeRunStartResult replayed = runtime.start(command);

        assertThat(replayed.runId()).isEqualTo(RUN_ID);
        verify(runRepository, times(1)).create(any());
        verify(eventService, times(1)).append(eq(RUN_ID), eq(CreativeEventType.RUN_ACCEPTED), any());
    }

    @Test
    void shouldRejectDifferentRequestUsingSameIdempotencyKey() {
        CreativeRun existing = CreativeRun.builder()
                .runId(RUN_ID)
                .requestId(REQUEST_ID)
                .requestFingerprint("different")
                .status(CreativeRunStatus.QUEUED)
                .build();
        when(runRepository.findByRequestId(USER_ID, REQUEST_ID)).thenReturn(Optional.of(existing));
        StartCreativeRunCommand command = StartCreativeRunCommand.manual(
                USER_ID, REQUEST_ID, PROJECT_ID, CreativeOperationType.GENERATE_CHAPTER_STORYBOARD,
                new CreativeResourceReference(CreativeResourceType.CHAPTER, 303L),
                AgentPermissionPolicy.PROPOSE_CHANGES, "生成本章分镜");

        assertThatThrownBy(() -> runtime.start(command))
                .isInstanceOfSatisfying(BusinessException.class, exception -> assertThat(
                        exception.getResponseCode()).isEqualTo(
                        CreativeRuntimeResponseCode.IDEMPOTENCY_KEY_CONFLICT));
        verify(runRepository, never()).create(any());
    }

    @Test
    void shouldCancelQueuedRunThroughStateMachine() {
        CreativeRun run = CreativeRun.builder()
                .runId(RUN_ID)
                .userId(USER_ID)
                .status(CreativeRunStatus.QUEUED)
                .nextSequence(2L)
                .build();
        when(runRepository.lockOwned(RUN_ID, USER_ID)).thenReturn(Optional.of(run));

        runtime.cancel(new CancelCreativeRunCommand(RUN_ID, USER_ID));

        assertThat(run.getStatus()).isEqualTo(CreativeRunStatus.CANCELLED);
        assertThat(run.getCancelRequestedAt()).isNotNull();
        assertThat(run.getFinishedAt()).isNotNull();
        verify(eventService).append(RUN_ID, CreativeEventType.RUN_CANCEL_REQUESTED, null);
        verify(eventService).append(RUN_ID, CreativeEventType.RUN_CANCELLED, null);
    }
}
