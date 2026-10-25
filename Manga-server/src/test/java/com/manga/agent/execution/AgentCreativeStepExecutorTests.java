package com.manga.agent.execution;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.manga.agent.blueprint.AgentBlueprint;
import com.manga.agent.blueprint.AgentBlueprintCodec;
import com.manga.agent.blueprint.AgentKernelKey;
import com.manga.agent.capability.AgentRuntimeContext;
import com.manga.agent.config.CreativeExecutionProperties;
import com.manga.agent.domain.AgentPermissionPolicy;
import com.manga.agent.domain.CreativeOperationType;
import com.manga.agent.domain.CreativeResourceReference;
import com.manga.agent.domain.CreativeResourceType;
import com.manga.agent.domain.CreativeStepInput;
import com.manga.agent.domain.CreativeStepType;
import com.manga.agent.domain.CreativeToolStatus;
import com.manga.agent.harness.MangaAgentInvoker;
import com.manga.agent.profile.AgentProfileKey;
import io.agentscope.core.event.AgentEvent;
import io.agentscope.core.event.TextBlockDeltaEvent;
import io.agentscope.core.event.ToolResultEndEvent;
import io.agentscope.core.event.ToolResultStartEvent;
import io.agentscope.core.message.ToolResultState;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.scheduling.TaskScheduler;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 验证 Agent Step 会恢复冻结 Blueprint、流式提交公开事件并使用围栏完成。
 */
class AgentCreativeStepExecutorTests {

    /** 测试租约时长，对应心跳每十秒续签一次。 */
    private static final Duration STEP_LEASE = Duration.ofSeconds(30);
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void shouldStreamAgentEventsAndCompleteWithAuditableOutput() throws Exception {
        AgentBlueprintCodec blueprintCodec = mock(AgentBlueprintCodec.class);
        MangaAgentInvoker invoker = mock(MangaAgentInvoker.class);
        CreativeStepLifecycleService lifecycleService = mock(CreativeStepLifecycleService.class);
        TaskScheduler scheduler = mock(TaskScheduler.class);
        @SuppressWarnings("rawtypes")
        ScheduledFuture scheduledFuture = mock(ScheduledFuture.class);
        AgentBlueprint blueprint = blueprint();
        ClaimedCreativeStep claimed = claimed(objectMapper.writeValueAsString(new CreativeStepInput(
                "解析剧本", CreativeOperationType.PARSE_SCRIPT,
                new CreativeResourceReference(CreativeResourceType.SCRIPT, 31L))));
        when(blueprintCodec.deserialize(claimed.blueprintSnapshotJson())).thenReturn(blueprint);
        doReturn(scheduledFuture).when(scheduler).scheduleAtFixedRate(
                any(Runnable.class), any(Instant.class), eq(Duration.ofSeconds(10)));
        ArgumentCaptor<AgentRuntimeContext> context = ArgumentCaptor.forClass(AgentRuntimeContext.class);
        when(invoker.invoke(eq(blueprint), context.capture(), eq("解析剧本"))).thenReturn(Flux.just(
                new TextBlockDeltaEvent("reply-1", "block-1", "第一段"),
                new ToolResultStartEvent("reply-1", "tool-1", "get_script_structure"),
                new ToolResultEndEvent("reply-1", "tool-1", "get_script_structure", ToolResultState.SUCCESS),
                new TextBlockDeltaEvent("reply-1", "block-1", "第二段")
        ).cast(AgentEvent.class));
        when(lifecycleService.appendDelta(any(), any())).thenReturn(true);
        when(lifecycleService.appendToolStarted(any(), any(), any())).thenReturn(true);
        when(lifecycleService.appendToolCompleted(any(), any(), any(), any())).thenReturn(true);
        when(lifecycleService.canContinue(claimed)).thenReturn(true);
        when(lifecycleService.complete(any(), any())).thenReturn(true);
        AgentCreativeStepExecutor executor = new AgentCreativeStepExecutor(
                blueprintCodec,
                invoker,
                lifecycleService,
                properties(),
                scheduler,
                objectMapper);

        executor.execute(claimed);

        assertThat(context.getValue().stateSessionId()).isEqualTo("state-session-1");
        assertThat(context.getValue().projectId()).isEqualTo(21L);
        verify(lifecycleService).appendDelta(claimed, "第一段");
        verify(lifecycleService).appendToolStarted(claimed, "tool-1", "get_script_structure");
        verify(lifecycleService).appendToolCompleted(
                claimed, "tool-1", "get_script_structure", CreativeToolStatus.SUCCESS);
        verify(lifecycleService).appendDelta(claimed, "第二段");
        verify(lifecycleService).complete(claimed, "{\"content\":\"第一段第二段\"}");
        verify(scheduledFuture).cancel(false);
    }

    /** 构造测试用且指纹稳定的最小 Blueprint。 */
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

    /** 构造携带完整 Run 上下文和执行围栏的领取快照。 */
    private ClaimedCreativeStep claimed(String inputJson) {
        return new ClaimedCreativeStep(
                "step-1",
                "run-1",
                CreativeStepType.AGENT_INVOCATION,
                "instance-1",
                2L,
                inputJson,
                11L,
                21L,
                null,
                LocalDateTime.now().plusMinutes(5),
                AgentPermissionPolicy.READ_ONLY,
                "{\"snapshot\":true}",
                "fingerprint-1",
                "state-session-1");
    }

    /** 构造测试使用的 Step 调度与租约参数。 */
    private CreativeExecutionProperties properties() {
        return new CreativeExecutionProperties(
                STEP_LEASE,
                Duration.ofSeconds(30),
                Duration.ofSeconds(1),
                Duration.ofMinutes(1),
                Duration.ZERO,
                50);
    }
}
