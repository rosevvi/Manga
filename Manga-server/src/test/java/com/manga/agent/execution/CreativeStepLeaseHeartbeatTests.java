package com.manga.agent.execution;

import com.manga.agent.domain.AgentPermissionPolicy;
import com.manga.agent.domain.CreativeErrorCode;
import com.manga.agent.domain.CreativeStepType;
import com.manga.agent.harness.AgentRuntimeException;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.scheduling.TaskScheduler;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.concurrent.ScheduledFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 验证数据库 Step 租约续签失败会中止仍在流式运行的 Agent。
 */
class CreativeStepLeaseHeartbeatTests {

    @Test
    void shouldPublishOwnerLeaseLostWhenRenewalFails() {
        CreativeStepLifecycleService lifecycleService = mock(CreativeStepLifecycleService.class);
        TaskScheduler scheduler = mock(TaskScheduler.class);
        @SuppressWarnings("rawtypes")
        ScheduledFuture scheduledFuture = mock(ScheduledFuture.class);
        ArgumentCaptor<Runnable> task = ArgumentCaptor.forClass(Runnable.class);
        ClaimedCreativeStep step = claimed();
        doReturn(scheduledFuture).when(scheduler).scheduleAtFixedRate(
                task.capture(), any(Instant.class), eq(Duration.ofSeconds(10)));
        when(lifecycleService.renewLease(step)).thenReturn(false);
        CreativeStepLeaseHeartbeat heartbeat = new CreativeStepLeaseHeartbeat(
                step, lifecycleService, Duration.ofSeconds(30), scheduler);

        StepVerifier.create(heartbeat.lossSignal())
                .then(() -> task.getValue().run())
                .expectErrorSatisfies(error -> assertThat(error)
                        .isInstanceOfSatisfying(AgentRuntimeException.class,
                                failure -> assertThat(failure.errorCode())
                                        .isEqualTo(CreativeErrorCode.OWNER_LEASE_LOST)))
                .verify();

        verify(scheduledFuture).cancel(false);
    }

    /** 构造仍在有效期限内的领取快照。 */
    private ClaimedCreativeStep claimed() {
        return new ClaimedCreativeStep(
                "step-1",
                "run-1",
                CreativeStepType.AGENT_INVOCATION,
                "instance-1",
                2L,
                "{}",
                11L,
                21L,
                null,
                LocalDateTime.now().plusMinutes(5),
                AgentPermissionPolicy.READ_ONLY,
                "{}",
                "fingerprint-1",
                "state-session-1");
    }
}
