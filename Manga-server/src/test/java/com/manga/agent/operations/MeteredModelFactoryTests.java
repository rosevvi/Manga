package com.manga.agent.operations;

import com.manga.agent.CreativeAgentTestSupport;
import com.manga.agent.capability.AgentRuntimeContext;
import com.manga.agent.domain.AgentPermissionPolicy;
import com.manga.agent.domain.CreativeErrorCode;
import com.manga.agent.execution.CreativeExecutionFailure;
import com.manga.entity.ModelCallLedger;
import io.agentscope.core.model.ChatResponse;
import io.agentscope.core.model.Model;
import io.agentscope.core.model.GenerateOptions;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.TaskScheduler;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;
import reactor.test.StepVerifier;
import reactor.test.scheduler.VirtualTimeScheduler;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.math.BigDecimal;
import java.util.Map;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/** 用虚拟时钟验证真实计时操作符，不发供应商请求、不等待数分钟。 */
class MeteredModelFactoryTests {
    private final ModelUsageService usage = mock(ModelUsageService.class);
    private final Model delegate = mock(Model.class);

    /** 持续输出超过两分钟仍可完成，SDK 被显式限制为单次尝试。 */
    @Test
    void continuousStreamSurvivesTwoMinutes() {
        VirtualTimeScheduler timer = VirtualTimeScheduler.create();
        try {
            when(delegate.stream(any(), any(), any())).thenAnswer(invocation -> {
                var options = invocation.getArgument(2, GenerateOptions.class);
                assertThat(options.getExecutionConfig().getMaxAttempts()).isEqualTo(1);
                return Flux.interval(Duration.ofSeconds(50), timer).take(4).map(index -> mock(ChatResponse.class));
            });
            Model model = wrapped(timer);
            StepVerifier.withVirtualTime(() -> model.stream(List.of(), List.of(), null), () -> timer, Long.MAX_VALUE)
                    .thenAwait(Duration.ofSeconds(201)).expectNextCount(4).verifyComplete();
            verify(usage).finish(any(), eq(ModelCallStatus.SUCCEEDED), any());
        } finally { timer.dispose(); }
    }

    /** 供应商不再发数据，两分钟后明确停止，调用不自动重发。 */
    @Test
    void idleStreamFailsOnce() {
        VirtualTimeScheduler timer = VirtualTimeScheduler.create();
        try {
            when(delegate.stream(any(), any(), any())).thenReturn(Flux.never());
            Model model = wrapped(timer);
            StepVerifier.withVirtualTime(() -> model.stream(List.of(), List.of(), null), () -> timer, Long.MAX_VALUE)
                    .thenAwait(Duration.ofMinutes(2).plusSeconds(1))
                    .expectErrorSatisfies(failure -> assertThat(CreativeExecutionFailure.classify(failure).errorCode()).isEqualTo(CreativeErrorCode.MODEL_IDLE_TIMEOUT)).verify();
            verify(delegate, times(1)).stream(any(), any(), any());
            verify(usage).finish(any(), eq(ModelCallStatus.FAILED), any());
        } finally { timer.dispose(); }
    }

    /** 连续数据不会延长已冻结的五分钟总时限。 */
    @Test
    void continuousStreamStopsAtTotalDeadline() {
        VirtualTimeScheduler timer = VirtualTimeScheduler.create();
        try {
            when(delegate.stream(any(), any(), any())).thenReturn(Flux.interval(Duration.ofSeconds(20), timer).map(index -> mock(ChatResponse.class)));
            Model model = wrapped(timer);
            StepVerifier.withVirtualTime(() -> model.stream(List.of(), List.of(), null), () -> timer, Long.MAX_VALUE)
                    .thenAwait(Duration.ofMinutes(5).plusSeconds(1)).expectNextCount(14)
                    .expectErrorSatisfies(failure -> assertThat(CreativeExecutionFailure.classify(failure).errorCode()).isEqualTo(CreativeErrorCode.MODEL_TIMEOUT)).verify();
            verify(delegate, times(1)).stream(any(), any(), any());
        } finally { timer.dispose(); }
    }

    /** 使用真实包装器和无网络账本边界，精确控制时钟。 */
    private Model wrapped(VirtualTimeScheduler timer) {
        ModelCallLedger call = new ModelCallLedger();
        call.setStartedAt(LocalDateTime.now(ZoneOffset.UTC)); call.setExpiresAt(call.getStartedAt().plusMinutes(5));
        call.setStatus(ModelCallStatus.STARTED);
        when(usage.begin(any(), any())).thenReturn(call);
        RuntimeOperationsProperties properties = new RuntimeOperationsProperties(4, 60, 3, Duration.ofSeconds(30),
                Duration.ofMinutes(2), Duration.ofMinutes(5), Duration.ofMinutes(2), new BigDecimal("0.05"),
                BigDecimal.TEN, Map.of(), Duration.ofDays(30), 10000);
        var factory = new MeteredModelFactory(usage, Schedulers.immediate(), timer, properties);
        var context = new AgentRuntimeContext(1L, null, null, "run", "step", "session", "owner", 1L,
                Instant.now().plusSeconds(600), AgentPermissionPolicy.READ_ONLY);
        return factory.wrap(delegate, CreativeAgentTestSupport.blueprint().blueprint(), context);
    }
}
