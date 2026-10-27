package com.manga.agent.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.manga.agent.CreativeAgentTestSupport;
import com.manga.agent.api.CreativeConversationHistory;
import com.manga.agent.blueprint.AgentBlueprintCompiler;
import com.manga.agent.config.CreativeMemoryProperties;
import com.manga.agent.domain.CreativeErrorCode;
import com.manga.agent.domain.CreativeEventType;
import com.manga.agent.domain.CreativeMemoryConstants;
import com.manga.agent.domain.CreativeMessageRole;
import com.manga.agent.domain.CreativeRunStatus;
import com.manga.agent.domain.CreativeStepInput;
import com.manga.agent.domain.CreativeToolStatus;
import com.manga.agent.event.ContentDeltaEventPayload;
import com.manga.agent.event.CreativeEventSources;
import com.manga.agent.event.RunAcceptedEventPayload;
import com.manga.agent.event.ToolExecutionEventPayload;
import com.manga.agent.execution.AgentCreativeStepExecutor;
import com.manga.agent.execution.ClaimedCreativeStep;
import com.manga.agent.execution.CreativeStepClaimService;
import com.manga.agent.execution.CreativeStepDispatcher;
import com.manga.agent.execution.CreativeStepLifecycleService;
import com.manga.agent.harness.AgentRuntimeException;
import com.manga.agent.harness.MangaAgentInvoker;
import com.manga.agent.state.AgentStateSessionService;
import com.manga.common.enums.UserRole;
import com.manga.common.exception.BusinessException;
import com.manga.entity.AgentStateSession;
import com.manga.entity.CreativeConversation;
import com.manga.entity.CreativeEvent;
import com.manga.entity.CreativeMessage;
import com.manga.entity.CreativeRun;
import com.manga.entity.CreativeStep;
import com.manga.mapper.AgentStateSessionMapper;
import com.manga.mapper.CreativeEventMapper;
import com.manga.repository.AgentStateSessionRepository;
import com.manga.repository.CreativeConversationRepository;
import com.manga.repository.CreativeEventRepository;
import com.manga.repository.CreativeMessageRepository;
import com.manga.repository.CreativeRunRepository;
import com.manga.repository.CreativeStepRepository;
import com.manga.repository.MangaAgentStateRepository;
import com.manga.service.TokenService;
import io.agentscope.core.event.AgentEvent;
import io.agentscope.core.event.TextBlockDeltaEvent;
import io.agentscope.core.state.AgentState;
import io.agentscope.core.state.AgentStateStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import reactor.core.publisher.Flux;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 使用真实事务、Mapper、投影与执行器验收连续对话，仅替换外部模型和自动调度边界。 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:manga_stage3;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
        "manga.creative.memory.projection-batch-size=2",
        "manga.creative.memory.history-page-size=10",
        "manga.creative.memory.context-max-chars=1024",
        "manga.creative.memory.summary-max-chars=256",
        "manga.creative.memory.recent-messages=2",
        "manga.creative.memory.maintenance-interval=1h"
})
@ActiveProfiles("test")
@AutoConfigureMockMvc
class CreativeConversationMemoryIntegrationTests {
    /** 测试管理员用户，来自真实初始化器。 */
    private static final long USER_ID = 1L;
    @MockitoBean
    private AgentBlueprintCompiler compiler;
    @MockitoBean
    private MangaAgentInvoker invoker;
    @MockitoBean
    private CreativeStepDispatcher dispatcher;
    @Autowired
    private CreativeRuntime runtime;
    @Autowired
    private CreativeRunRepository runs;
    @Autowired
    private CreativeStepRepository steps;
    @Autowired
    private CreativeMessageRepository messages;
    @Autowired
    private CreativeEventRepository events;
    @Autowired
    private CreativeEventMapper eventMapper;
    @Autowired
    private CreativeEventService eventService;
    @Autowired
    private CreativeMessageProjector projector;
    @Autowired
    private CreativeConversationService conversations;
    @Autowired
    private CreativeConversationRepository conversationRepository;
    @Autowired
    private CreativeStepClaimService claimService;
    @Autowired
    private AgentCreativeStepExecutor executor;
    @Autowired
    private CreativeStepLifecycleService lifecycle;
    @Autowired
    private AgentStateStore stateStore;
    @Autowired
    private AgentStateSessionService stateSessions;
    @Autowired
    private AgentStateSessionRepository sessionRepository;
    @Autowired
    private AgentStateSessionMapper sessionMapper;
    @Autowired
    private CreativeMemoryProperties memoryProperties;
    @Autowired
    private MangaAgentStateRepository storedStates;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private TokenService tokenService;
    @Autowired
    private PlatformTransactionManager transactionManager;

    /** 提供真实 Codec 可验证的 Blueprint 和确定性模型流。 */
    @BeforeEach
    void modelBoundary() {
        when(compiler.compile(any())).thenReturn(CreativeAgentTestSupport.blueprint());
        when(invoker.invokeUntil(any(), any(), any(), any())).thenReturn(Flux.<AgentEvent>just(
                new TextBlockDeltaEvent("reply", "text", "已完成分析")));
    }

    @Test
    void duplicateProjectionAndRebuildPreserveTextToolsAndTerminalStatus() {
        CreativeRunStartResult run = start(null, "请记住角色叫阿青");
        eventService.append(run.runId(), CreativeEventType.TOOL_STARTED,
                new ToolExecutionEventPayload("call-1", "get_project_overview", CreativeToolStatus.RUNNING));
        eventService.append(run.runId(), CreativeEventType.TOOL_COMPLETED,
                new ToolExecutionEventPayload("call-1", "get_project_overview", CreativeToolStatus.SUCCESS));
        execute(run);
        CreativeConversationHistory before = conversations.history(USER_ID, run.conversationId(), null);

        assertThat(projector.project(run.runId())).isZero();
        assertThat(projector.project(run.runId())).isZero();
        conversations.rebuild(USER_ID, run.conversationId());
        CreativeConversationHistory rebuilt = conversations.history(USER_ID, run.conversationId(), null);

        assertThat(rebuilt.messages()).usingRecursiveComparison()
                .ignoringFields("id").isEqualTo(before.messages());
        assertThat(rebuilt.messages()).filteredOn(message -> message.role() == CreativeMessageRole.ASSISTANT)
                .singleElement().satisfies(message -> {
                    assertThat(message.content()).isEqualTo("已完成分析");
                    assertThat(message.status()).isEqualTo(CreativeRunStatus.COMPLETED);
                });
        assertThat(rebuilt.messages()).filteredOn(message -> message.role() == CreativeMessageRole.TOOL)
                .singleElement().satisfies(message -> assertThat(message.toolStatus()).isEqualTo(CreativeToolStatus.SUCCESS));
        assertThat(rebuilt.activeRun()).isNull();
        assertThat(runs.findByRunId(run.runId()).orElseThrow().getProjectedThroughSequence())
                .isEqualTo(runs.findByRunId(run.runId()).orElseThrow().getTerminalSequence());
    }

    @Test
    void projectionFailureRollsBackWholeBatchAndResumesFromCommittedCursor() {
        CreativeRunStartResult started = start(null, "检查投影恢复");
        CreativeRun run = runs.findByRunId(started.runId()).orElseThrow();
        long cursor = run.getProjectedThroughSequence();
        appendRaw(run, cursor + 1L, "{\"delta\":\"A\"}");
        CreativeEvent broken = appendRaw(run, cursor + 2L, "{broken");
        run.setNextSequence(cursor + 3L);
        runs.update(run);

        assertThatThrownBy(() -> projector.project(run.getRunId())).isInstanceOf(IllegalStateException.class);
        assertThat(runs.findByRunId(run.getRunId()).orElseThrow().getProjectedThroughSequence()).isEqualTo(cursor);
        assertThat(assistant(run.getRunId()).getContent()).isEmpty();

        broken.setPayloadJson("{\"delta\":\"B\"}");
        eventMapper.updateById(broken);
        assertThat(projector.project(run.getRunId())).isEqualTo(2);
        assertThat(assistant(run.getRunId()).getContent()).isEqualTo("AB");
        assertThat(projector.project(run.getRunId())).isZero();
    }

    @Test
    void concurrentProjectorsDoNotDuplicateText() throws Exception {
        CreativeRunStartResult started = start(null, "并发消息投影");
        CreativeRun run = runs.findByRunId(started.runId()).orElseThrow();
        appendRaw(run, run.getNextSequence(), "{\"delta\":\"只追加一次\"}");
        run.setNextSequence(run.getNextSequence() + 1L);
        runs.update(run);
        CountDownLatch ready = new CountDownLatch(1);
        try (ExecutorService tasks = Executors.newVirtualThreadPerTaskExecutor()) {
            Future<Integer> first = tasks.submit(() -> {
                await(ready);
                return projector.project(run.getRunId());
            });
            Future<Integer> second = tasks.submit(() -> {
                await(ready);
                return projector.project(run.getRunId());
            });
            ready.countDown();
            assertThat(first.get(3, TimeUnit.SECONDS)
                    + second.get(3, TimeUnit.SECONDS)).isEqualTo(1);
        }
        assertThat(assistant(run.getRunId()).getContent()).isEqualTo("只追加一次");
        runtime.cancel(new CancelCreativeRunCommand(run.getRunId(), USER_ID));
    }

    @Test
    void cancellationRevokesLateStateAndNextRunUsesNewGenerationAndCleanContext() throws Exception {
        CreativeRunStartResult first = start(null, "成功轮次中的角色约定");
        execute(first);
        CreativeRunStartResult cancelled = start(first.conversationId(), "取消轮次中的污染标记");
        ClaimedCreativeStep claimed = claim(cancelled);
        eventService.append(cancelled.runId(), CreativeEventType.TOOL_STARTED,
                new ToolExecutionEventPayload("call-cancel", "get_script_structure", CreativeToolStatus.RUNNING));
        stateStore.save("1", claimed.stateSessionId(), "director", state(claimed.stateSessionId(), "old memory"));
        runtime.cancel(new CancelCreativeRunCommand(cancelled.runId(), USER_ID));
        assertThat(conversations.history(USER_ID, first.conversationId(), null).activeRun().runId())
                .isEqualTo(cancelled.runId());

        assertThatThrownBy(() -> stateStore.save("1", claimed.stateSessionId(), "director",
                state(claimed.stateSessionId(), "late memory"))).isInstanceOf(AgentRuntimeException.class);
        assertThatThrownBy(() -> stateStore.saveIfVersion("1", claimed.stateSessionId(), "director",
                state(claimed.stateSessionId(), "late CAS"), 1L)).isInstanceOf(AgentRuntimeException.class);
        assertThatThrownBy(() -> stateStore.save("1", claimed.stateSessionId(), "list",
                List.of(state(claimed.stateSessionId(), "late list")))).isInstanceOf(AgentRuntimeException.class);

        CreativeRunStartResult next = start(first.conversationId(), "继续创作");
        // 新轮已受理后，旧 owner 才确认取消；旧终态不能撤销新轮状态会话或再次推进代次。
        lifecycle.acknowledgeCancellation(claimed);
        CreativeRun nextRun = runs.findByRunId(next.runId()).orElseThrow();
        CreativeStep nextStep = steps.findByRunId(next.runId()).getFirst();
        CreativeStepInput input = objectMapper.readValue(nextStep.getInputJson(), CreativeStepInput.class);
        assertThat(nextRun.getStateGeneration()).isEqualTo(2L);
        assertThat(nextStep.getStateSessionId()).isNotEqualTo(claimed.stateSessionId());
        assertThat(input.instruction()).contains("成功轮次中的角色约定", "已完成分析", "继续创作")
                .doesNotContain("取消轮次中的污染标记", "late memory");
        execute(next);
        assertThat(conversations.history(USER_ID, first.conversationId(), null).messages())
                .filteredOn(message -> message.role() == CreativeMessageRole.TOOL)
                .singleElement().satisfies(message -> assertThat(message.toolStatus()).isEqualTo(CreativeToolStatus.INTERRUPTED));
    }

    @Test
    void failedRunDoesNotEnterNextTurnsContext() throws Exception {
        CreativeRunStartResult failed = start(null, "失败轮次污染标记");
        ClaimedCreativeStep claimed = claim(failed);
        lifecycle.appendDelta(claimed, "未完成的错误回答");
        lifecycle.fail(claimed, new AgentRuntimeException(CreativeErrorCode.MODEL_INVOCATION_FAILED, "test failure"));
        CreativeRunStartResult next = start(failed.conversationId(), "新一轮请求");
        String input = objectMapper.readValue(steps.findByRunId(next.runId()).getFirst().getInputJson(),
                CreativeStepInput.class).instruction();
        assertThat(input).isEqualTo("新一轮请求");
        assertThat(runs.findByRunId(next.runId()).orElseThrow().getStateGeneration()).isEqualTo(2L);
        assertThatThrownBy(() -> stateStore.save("1", claimed.stateSessionId(), "director",
                state(claimed.stateSessionId(), "late"))).isInstanceOf(AgentRuntimeException.class);
        execute(next);
    }

    @Test
    void longConversationCompactsAndStillExecutesNextTurn() throws Exception {
        String conversationId = null;
        for (int turn = 0; turn < 8; turn++) {
            CreativeRunStartResult run = start(conversationId, "第" + turn + "轮约定：" + "剧情资料".repeat(120));
            conversationId = run.conversationId();
            execute(run);
        }
        CreativeRunStartResult next = start(conversationId, "压缩后继续推进故事");
        CreativeConversation stored = conversationRepository.findOwned(conversationId, USER_ID).orElseThrow();
        String context = objectMapper.readValue(steps.findByRunId(next.runId()).getFirst().getInputJson(),
                CreativeStepInput.class).instruction();
        assertThat(stored.getMemorySummary()).isNotBlank().hasSizeLessThanOrEqualTo(memoryProperties.summaryMaxChars());
        assertThat(stored.getSummarizedThroughMessageId()).isPositive();
        assertThat(context).contains("压缩后继续推进故事", "ASSISTANT", "<history>")
                .hasSizeLessThan(memoryProperties.contextMaxChars() + 200);
        execute(next);
        assertThat(runs.findByRunId(next.runId()).orElseThrow().getStatus()).isEqualTo(CreativeRunStatus.COMPLETED);
        assertThat(messages.history(conversationId, null, 100)).hasSize(18);
    }

    @Test
    void expiredStateCleanupKeepsEventsAndPreventsSessionResurrection() {
        CreativeRun run = runs.create(CreativeAgentTestSupport.run(USER_ID));
        String sessionId = UUID.randomUUID().toString();
        stateSessions.register(run, sessionId);
        stateStore.save("1", sessionId, "director", state(sessionId, "expired memory"));
        AgentStateSession session = sessionRepository.lock("1", sessionId).orElseThrow();
        session.setExpiresAt(LocalDateTime.now().minusDays(10));
        sessionMapper.updateById(session);
        stateSessions.purge(session, LocalDateTime.now().minusDays(7));

        assertThat(storedStates.exists("1", sessionId)).isFalse();
        assertThat(sessionRepository.lock("1", sessionId)).isEmpty();
        assertThatThrownBy(() -> stateStore.save("1", sessionId, "director", state(sessionId, "resurrect")))
                .isInstanceOf(AgentRuntimeException.class);
        assertThat(runs.findByRunId(run.getRunId())).isPresent();
    }

    @Test
    void historyChecksOwnershipAndRebuildRejectsActiveRun() {
        CreativeRunStartResult run = start(null, "私有会话");
        assertThatThrownBy(() -> conversations.history(USER_ID + 999L, run.conversationId(), null))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> conversations.rebuild(USER_ID, run.conversationId()))
                .isInstanceOf(BusinessException.class);
        runtime.cancel(new CancelCreativeRunCommand(run.runId(), USER_ID));
    }

    @Test
    void cancellationWaitsForInFlightStateWriteAndThenRejectsAllLateWrites() throws Exception {
        CreativeRunStartResult started = start(null, "并发状态隔离");
        String sessionId = steps.findByRunId(started.runId()).getFirst().getStateSessionId();
        CountDownLatch locked = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        TransactionTemplate transaction =
                new TransactionTemplate(transactionManager);
        try (ExecutorService tasks = Executors.newVirtualThreadPerTaskExecutor()) {
            Future<?> writer = tasks.submit(() -> transaction.executeWithoutResult(status -> {
                stateStore.save("1", sessionId, "director", state(sessionId, "before cancellation"));
                locked.countDown();
                await(release);
            }));
            try {
                assertThat(locked.await(2, TimeUnit.SECONDS)).isTrue();
                Future<?> cancellation = tasks.submit(
                        () -> runtime.cancel(new CancelCreativeRunCommand(started.runId(), USER_ID)));
                assertThatThrownBy(() -> cancellation.get(100, TimeUnit.MILLISECONDS))
                        .isInstanceOf(TimeoutException.class);
                release.countDown();
                writer.get(3, TimeUnit.SECONDS);
                cancellation.get(3, TimeUnit.SECONDS);
                assertThatThrownBy(() -> stateStore.save("1", sessionId, "director", state(sessionId, "late")))
                        .isInstanceOf(AgentRuntimeException.class);
            } finally { release.countDown(); }
        }
    }

    @Test
    void historyHttpEndpointRestoresSameMessagesAndChecksOwnership() throws Exception {
        CreativeRunStartResult run = start(null, "服务端历史恢复");
        execute(run);
        String token = tokenService.issue(USER_ID, "admin", "Administrator",
                Set.of(UserRole.ADMIN), null, false).accessToken();
        String path = "/api/v1/creative-conversations/" + run.conversationId() + "/messages";
        mockMvc.perform(get(path)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.messages[0].content")
                        .value("服务端历史恢复"))
                .andExpect(jsonPath("$.data.messages[1].content")
                        .value("已完成分析"));
        String first = mockMvc.perform(get(path)
                        .header("Authorization", "Bearer " + token)).andReturn().getResponse().getContentAsString();
        String second = mockMvc.perform(get(path)
                        .header("Authorization", "Bearer " + token)).andReturn().getResponse().getContentAsString();
        assertThat(objectMapper.readTree(second).get("data")).isEqualTo(objectMapper.readTree(first).get("data"));
        mockMvc.perform(get(path))
                .andExpect(status().isUnauthorized());
        String otherToken = tokenService.issue(USER_ID + 100L, "another-user", "Another User",
                Set.of(UserRole.ADMIN), null, false).accessToken();
        mockMvc.perform(get(path)
                        .header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isForbidden());
    }

    /** 等待测试事务释放锁，超时或中断都立即失败，避免无界挂起。 */
    private void await(CountDownLatch latch) {
        try { assertThat(latch.await(3, TimeUnit.SECONDS)).isTrue(); }
        catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Test state writer interrupted", exception);
        }
    }

    /** 创建实际受理事务；测试调度替身避免与手动领取竞争。 */
    private CreativeRunStartResult start(String conversationId, String instruction) {
        return runtime.start(StartCreativeRunCommand.conversation(USER_ID,
                UUID.randomUUID().toString(), conversationId, null, instruction));
    }

    /** 使用真实数据库领取 Step。 */
    private ClaimedCreativeStep claim(CreativeRunStartResult run) {
        CreativeStep step = steps.findByRunId(run.runId()).getFirst();
        return claimService.claim(step.getStepId(), step.getScheduleVersion()).orElseThrow();
    }

    /** 执行真实 Agent Step、事件投影和 Run 聚合。 */
    private void execute(CreativeRunStartResult run) {
        executor.execute(claim(run));
    }

    /** 查询当前助手正文，供投影事务回滚断言使用。 */
    private CreativeMessage assistant(String runId) {
        return messages.find(CreativeMessageProjector.projectionKey(runId, CreativeMemoryConstants.ASSISTANT_KEY)).orElseThrow();
    }

    /** 绕过投影写入事实，模拟进程中断或存在损坏事件后的恢复。 */
    private CreativeEvent appendRaw(CreativeRun run, long sequence, String payload) {
        return events.create(CreativeEvent.builder().runId(run.getRunId()).sequenceNo(sequence).schemaVersion(1)
                .eventType(CreativeEventType.CONTENT_DELTA).source(CreativeEventSources.AGENT_SCOPE)
                .payloadJson(payload).createdBy("test:projection").updatedBy("test:projection").build());
    }

    /** 创建可序列化的内部 Agent 状态。 */
    private AgentState state(String sessionId, String summary) {
        return AgentState.builder().userId("1").sessionId(sessionId).summary(summary).build();
    }
}
