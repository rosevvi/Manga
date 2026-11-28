package com.manga.agent.operations;

import com.manga.agent.CreativeAgentTestSupport;
import com.manga.agent.blueprint.AgentBlueprint;
import com.manga.agent.blueprint.ModelSnapshot;
import com.manga.common.enums.ModelCallTimeout;
import com.manga.agent.capability.AgentRuntimeContext;
import com.manga.agent.domain.AgentPermissionPolicy;
import com.manga.agent.domain.CreativeOperationType;
import com.manga.agent.domain.CreativeRunStatus;
import com.manga.agent.domain.CreativeStepStatus;
import com.manga.entity.CreativeRun;
import com.manga.entity.CreativeStep;
import com.manga.entity.ProviderCallGate;
import com.manga.repository.CreativeRunRepository;
import com.manga.repository.CreativeStepRepository;
import com.manga.repository.RuntimeOperationsRepository;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 验证冻结模型时限与 Run 期限共同确定模型调用的账本截止时间。 */
class ModelUsageServiceTests {

    @ParameterizedTest
    @CsvSource({
            "MINUTES_5, 14400, 300",
            "MINUTES_15, 14400, 900",
            "MINUTES_30, 14400, 1800",
            "MINUTES_60, 14400, 3600",
            "HOURS_2, 14400, 7200",
            "HOURS_2, 60, 0",
            "UNLIMITED, 14400, 0",
            ", 14400, 1800"
    })
    void persistsDeadlineForModelAndRespectsRunDeadline(ModelCallTimeout timeout, int runSeconds,
            int expectedCallSeconds) {
        var runs = mock(CreativeRunRepository.class);
        var steps = mock(CreativeStepRepository.class);
        var operations = mock(RuntimeOperationsRepository.class);
        var limits = new RuntimeOperationsProperties(4, 60, 3, Duration.ofSeconds(30),
                Duration.ofMinutes(2), Duration.ofDays(30), 10000);
        var original = CreativeAgentTestSupport.blueprint().blueprint().model();
        var model = mock(AgentBlueprint.class);
        when(model.model()).thenReturn(new ModelSnapshot(original.configId(), original.providerType(), original.baseUrl(),
                original.modelName(), original.securityFingerprint(), original.contextWindow(), timeout));
        var context = new AgentRuntimeContext(1L, 2L, null, "run", "step", "session", "owner", 1L,
                Instant.now().plusSeconds(runSeconds), AgentPermissionPolicy.AUTOMATION);
        when(runs.lockByRunId(context.runId())).thenReturn(CreativeRun.builder().runId(context.runId())
                .projectId(context.projectId()).userId(context.userId()).operationType(CreativeOperationType.EXTRACT_CHAPTER_ASSETS)
                .status(CreativeRunStatus.ACTIVE).deadlineAt(LocalDateTime.now().plusSeconds(runSeconds)).build());
        when(steps.findByStepId(context.stepId())).thenReturn(Optional.of(CreativeStep.builder().runId(context.runId())
                .status(CreativeStepStatus.RUNNING).ownerInstanceId(context.ownerInstanceId()).ownerEpoch(context.ownerEpoch())
                .stateSessionId(context.stateSessionId()).leaseUntil(LocalDateTime.now().plusMinutes(1)).build()));
        var gate = new ProviderCallGate();
        gate.setConfigId(model.model().configId());
        gate.setConsecutiveFailures(0);
        gate.setWindowRequests(0);
        gate.setWindowStartedAt(LocalDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.MINUTES));
        when(operations.lockGate(anyLong(), anyString())).thenReturn(gate);

        var service = new ModelUsageService(operations, runs, steps, limits, mock(MeterRegistry.class));
        var call = service.begin(model, context);
        var expected = expectedCallSeconds == 0 ? LocalDateTime.ofInstant(context.deadline(), ZoneOffset.UTC)
                : call.getStartedAt().plusSeconds(expectedCallSeconds);
        assertThat(call.getExpiresAt()).isEqualTo(expected);
        verify(operations).create(call);
    }
}
