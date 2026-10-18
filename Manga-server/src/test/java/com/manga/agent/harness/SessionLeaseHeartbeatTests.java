package com.manga.agent.harness;

import com.manga.agent.domain.CreativeErrorCode;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.scheduling.TaskScheduler;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ScheduledFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * 验证状态会话续租失败会中止 Agent 流而不是静默丢失互斥保护。
 */
class SessionLeaseHeartbeatTests {

    @Test
    void shouldPublishLeaseRenewalFailure() {
        SessionGateLease lease = mock(SessionGateLease.class);
        TaskScheduler scheduler = mock(TaskScheduler.class);
        @SuppressWarnings("rawtypes")
        ScheduledFuture scheduledFuture = mock(ScheduledFuture.class);
        ArgumentCaptor<Runnable> task = ArgumentCaptor.forClass(Runnable.class);
        AgentRuntimeException failure = new AgentRuntimeException(
                CreativeErrorCode.STATE_STORE_UNAVAILABLE,
                "test renewal failure");
        doReturn(scheduledFuture).when(scheduler)
                .scheduleAtFixedRate(
                        task.capture(),
                        any(Instant.class),
                        eq(Duration.ofSeconds(10)));
        doThrow(failure).when(lease).renew();
        SessionLeaseHeartbeat heartbeat = new SessionLeaseHeartbeat(
                lease,
                Duration.ofSeconds(30),
                scheduler);

        StepVerifier.create(heartbeat.lossSignal())
                .then(() -> task.getValue().run())
                .expectErrorSatisfies(error -> assertThat(error).isSameAs(failure))
                .verify();

        verify(scheduledFuture).cancel(false);
    }
}
