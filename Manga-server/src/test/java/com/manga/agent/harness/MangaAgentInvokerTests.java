package com.manga.agent.harness;

import com.manga.agent.blueprint.AgentBlueprint;
import com.manga.agent.capability.AgentRuntimeContext;
import com.manga.agent.config.AgentHarnessProperties;
import com.manga.agent.domain.AgentPermissionPolicy;
import com.manga.agent.domain.CreativeErrorCode;
import io.agentscope.core.event.AgentEvent;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.TaskScheduler;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ScheduledFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 验证 Agent 调用预检顺序和取消时的完整资源释放。
 */
class MangaAgentInvokerTests {

    @Test
    void shouldReleaseHarnessKernelAndSessionGateWhenSubscriptionIsCancelled() {
        SessionSerialGate gate = mock(SessionSerialGate.class);
        SessionGateLease gateLease = mock(SessionGateLease.class);
        AgentStateStorePreflight preflight = mock(AgentStateStorePreflight.class);
        AgentKernelPool pool = mock(AgentKernelPool.class);
        KernelHandle kernelHandle = mock(KernelHandle.class);
        HarnessLease harnessLease = mock(HarnessLease.class);
        TaskScheduler scheduler = mock(TaskScheduler.class);
        @SuppressWarnings("rawtypes")
        ScheduledFuture scheduledFuture = mock(ScheduledFuture.class);
        AgentBlueprint blueprint = mock(AgentBlueprint.class);
        AgentRuntimeContext context = context();
        when(gate.acquire(context.stateSessionId())).thenReturn(gateLease);
        when(pool.acquire(blueprint)).thenReturn(kernelHandle);
        when(kernelHandle.openHarness(context)).thenReturn(harnessLease);
        when(harnessLease.stream("inspect project")).thenReturn(Flux.<AgentEvent>never());
        doReturn(scheduledFuture).when(scheduler)
                .scheduleAtFixedRate(
                        any(Runnable.class),
                        any(Instant.class),
                        eq(Duration.ofSeconds(10)));
        MangaAgentInvoker invoker = new MangaAgentInvoker(
                gate,
                preflight,
                pool,
                scheduler,
                properties());

        Disposable subscription = invoker.invoke(blueprint, context, "inspect project").subscribe();
        subscription.dispose();

        verify(preflight).verify(context);
        verify(scheduledFuture).cancel(false);
        verify(harnessLease).close();
        verify(kernelHandle).close();
        verify(gateLease).close();
    }

    @Test
    void shouldNotAcquireKernelWhenStateStorePreflightFails() {
        SessionSerialGate gate = mock(SessionSerialGate.class);
        SessionGateLease gateLease = mock(SessionGateLease.class);
        AgentStateStorePreflight preflight = mock(AgentStateStorePreflight.class);
        AgentKernelPool pool = mock(AgentKernelPool.class);
        TaskScheduler scheduler = mock(TaskScheduler.class);
        AgentBlueprint blueprint = mock(AgentBlueprint.class);
        AgentRuntimeContext context = context();
        AgentRuntimeException failure = new AgentRuntimeException(
                CreativeErrorCode.STATE_STORE_UNAVAILABLE,
                "test state store unavailable");
        when(gate.acquire(context.stateSessionId())).thenReturn(gateLease);
        doThrow(failure).when(preflight).verify(context);
        MangaAgentInvoker invoker = new MangaAgentInvoker(
                gate,
                preflight,
                pool,
                scheduler,
                properties());

        assertThatThrownBy(() -> invoker.invoke(blueprint, context, "inspect project").blockLast())
                .isSameAs(failure);
        verify(pool, never()).acquire(any());
        verify(gateLease).close();
    }

    private AgentHarnessProperties properties() {
        return new AgentHarnessProperties(
                Duration.ofSeconds(30),
                4,
                Duration.ofSeconds(2),
                Duration.ofSeconds(5));
    }

    private AgentRuntimeContext context() {
        return new AgentRuntimeContext(
                42L,
                9L,
                "conversation-17",
                "run-17",
                "step-17",
                "state-session-17",
                "instance-a",
                3L,
                Instant.now().plusSeconds(60),
                AgentPermissionPolicy.READ_ONLY);
    }
}
