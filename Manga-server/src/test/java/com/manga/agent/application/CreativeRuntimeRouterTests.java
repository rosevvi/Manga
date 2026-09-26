package com.manga.agent.application;

import com.manga.agent.AgentRunStatus;
import com.manga.agent.config.CreativeRuntimeRoutingProperties;
import com.manga.agent.domain.CreativeOperationType;
import com.manga.agent.domain.CreativeResourceReference;
import com.manga.agent.domain.CreativeResourceType;
import com.manga.agent.domain.CreativeRunTriggerSource;
import com.manga.agent.domain.CreativeRuntimeType;
import com.manga.entity.AgentRun;
import com.manga.repository.AgentRunRepository;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 验证创作运行时的灰度选择和运行中路由稳定性。
 */
class CreativeRuntimeRouterTests {

    private static final long USER_ID = 101L;
    private static final long PROJECT_ID = 202L;
    private static final String RUN_ID = "run-test";

    @Test
    void shouldUseDefaultRuntimeWhenNoOverrideMatches() {
        RouterFixture fixture = fixture();

        fixture.router().start(command(null));

        assertThat(fixture.legacy().startedCommand()).isNotNull();
        assertThat(fixture.durable().startedCommand()).isNull();
    }

    @Test
    void shouldUseUserOverrideWhenProjectHasNoOverride() {
        RouterFixture fixture = fixture();
        fixture.properties().getUserOverrides().put(USER_ID, CreativeRuntimeType.DURABLE);

        fixture.router().start(command(PROJECT_ID));

        assertThat(fixture.durable().startedCommand()).isNotNull();
        assertThat(fixture.legacy().startedCommand()).isNull();
    }

    @Test
    void shouldPreferProjectOverrideOverUserOverride() {
        RouterFixture fixture = fixture();
        fixture.properties().getUserOverrides().put(USER_ID, CreativeRuntimeType.LEGACY);
        fixture.properties().getProjectOverrides().put(PROJECT_ID, CreativeRuntimeType.DURABLE);

        fixture.router().start(command(PROJECT_ID));

        assertThat(fixture.durable().startedCommand()).isNotNull();
        assertThat(fixture.legacy().startedCommand()).isNull();
    }

    @Test
    void shouldPreserveManualOperationAndTargetWhenRouting() {
        RouterFixture fixture = fixture();
        fixture.properties().setDefaultRuntime(CreativeRuntimeType.DURABLE);
        CreativeResourceReference chapter = new CreativeResourceReference(CreativeResourceType.CHAPTER, 303L);
        StartCreativeRunCommand command = StartCreativeRunCommand.manual(USER_ID, PROJECT_ID,
                CreativeOperationType.GENERATE_CHAPTER_STORYBOARD, chapter, "生成本章分镜");

        fixture.router().start(command);

        assertThat(fixture.durable().startedCommand()).isEqualTo(command);
        assertThat(command.triggerSource()).isEqualTo(CreativeRunTriggerSource.MANUAL);
        assertThat(command.conversationId()).isNull();
        assertThat(command.target()).isEqualTo(chapter);
    }

    @Test
    void shouldCancelThroughRuntimePersistedOnRun() {
        RouterFixture fixture = fixture();
        fixture.properties().setDefaultRuntime(CreativeRuntimeType.LEGACY);
        AgentRun run = AgentRun.builder()
                .runId(RUN_ID)
                .userId(USER_ID)
                .runtimeType(CreativeRuntimeType.DURABLE)
                .build();
        when(fixture.runRepository().findOwned(RUN_ID, USER_ID)).thenReturn(Optional.of(run));

        fixture.router().cancel(new CancelCreativeRunCommand(RUN_ID, USER_ID));

        assertThat(fixture.durable().cancelledCommand()).isEqualTo(new CancelCreativeRunCommand(RUN_ID, USER_ID));
        assertThat(fixture.legacy().cancelledCommand()).isNull();
    }

    private StartCreativeRunCommand command(Long projectId) {
        return StartCreativeRunCommand.conversation(USER_ID, null, projectId, "分析项目");
    }

    private RouterFixture fixture() {
        RecordingRuntime legacy = new RecordingRuntime(CreativeRuntimeType.LEGACY);
        RecordingRuntime durable = new RecordingRuntime(CreativeRuntimeType.DURABLE);
        CreativeRuntimeRoutingProperties properties = new CreativeRuntimeRoutingProperties();
        AgentRunRepository runRepository = mock(AgentRunRepository.class);
        CreativeRuntimeRouter router = new CreativeRuntimeRouter(List.of(legacy, durable), properties, runRepository);
        return new RouterFixture(router, legacy, durable, properties, runRepository);
    }

    private record RouterFixture(
            /** 被测路由器。 */
            CreativeRuntimeRouter router,
            /** Legacy 记录型运行时。 */
            RecordingRuntime legacy,
            /** Durable 记录型运行时。 */
            RecordingRuntime durable,
            /** 路由配置。 */
            CreativeRuntimeRoutingProperties properties,
            /** Run 持久化入口 Mock。 */
            AgentRunRepository runRepository
    ) {
    }

    private static final class RecordingRuntime implements CreativeRuntime {

        private final CreativeRuntimeType runtimeType;
        private StartCreativeRunCommand startedCommand;
        private CancelCreativeRunCommand cancelledCommand;

        private RecordingRuntime(CreativeRuntimeType runtimeType) {
            this.runtimeType = runtimeType;
        }

        @Override
        public CreativeRuntimeType runtimeType() {
            return runtimeType;
        }

        @Override
        public CreativeRunStartResult start(StartCreativeRunCommand command) {
            startedCommand = command;
            return new CreativeRunStartResult(RUN_ID, "conversation-test", command.projectId(),
                    AgentRunStatus.RUNNING, null, null, null, null);
        }

        @Override
        public void cancel(CancelCreativeRunCommand command) {
            cancelledCommand = command;
        }

        private StartCreativeRunCommand startedCommand() {
            return startedCommand;
        }

        private CancelCreativeRunCommand cancelledCommand() {
            return cancelledCommand;
        }
    }
}
