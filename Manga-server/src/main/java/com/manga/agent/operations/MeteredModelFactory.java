package com.manga.agent.operations;

import com.manga.agent.blueprint.AgentBlueprint;
import com.manga.agent.capability.AgentRuntimeContext;
import com.manga.agent.domain.CreativeErrorCode;
import com.manga.agent.execution.CreativeExecutionFailure;
import com.manga.agent.harness.AgentRuntimeException;
import lombok.extern.slf4j.Slf4j;
import java.util.concurrent.atomic.AtomicBoolean;
import com.manga.common.constant.AsyncConstants;
import com.manga.entity.ModelCallLedger;
import io.agentscope.core.model.Model;
import io.agentscope.core.model.ChatResponse;
import io.agentscope.core.model.ToolSchema;
import io.agentscope.core.model.GenerateOptions;
import io.agentscope.core.model.ExecutionConfig;
import io.agentscope.core.message.Msg;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;
import java.time.ZoneOffset;
import java.time.LocalDateTime;
import java.time.Duration;
import java.util.Objects;
import java.util.List;
import java.util.concurrent.TimeoutException;

/** 每个 Harness 使用独立包装器；Kernel 仍独占底层模型，包装器不关闭共享客户端。 */
@Component
@Slf4j
public class MeteredModelFactory {
    /** 单个账本调用只对应一次供应商尝试，覆盖 SDK 默认三次自动重试。 */
    private final ModelUsageService usage;
    private final Scheduler scheduler;
    private final Scheduler streamTimer;
    private final RuntimeOperationsProperties properties;

    public MeteredModelFactory(ModelUsageService usage, @Qualifier(AsyncConstants.MANGA_BLOCKING_SCHEDULER) Scheduler scheduler,
            @Qualifier(AsyncConstants.MANGA_STREAM_TIMER) Scheduler streamTimer,
            RuntimeOperationsProperties properties) {
        this.usage = usage; this.scheduler = scheduler;
        this.streamTimer = streamTimer; this.properties = properties;
    }

    /** 覆盖正常推理和 Compaction 的每一次 Model.stream 订阅。 */
    public Model wrap(Model delegate, AgentBlueprint blueprint, AgentRuntimeContext context) {
        return new Model() {
            @Override public String getModelName() { return delegate.getModelName(); }
            @Override public int getContextWindowSize() { return delegate.getContextWindowSize(); }
            @Override public boolean supportsNativeStructuredOutput() { return delegate.supportsNativeStructuredOutput(); }
            @Override public boolean supportsNativeStructuredOutputWithTools() { return delegate.supportsNativeStructuredOutputWithTools(); }
            @Override
            public Flux<ChatResponse> stream(List<Msg> messages, List<ToolSchema> tools, GenerateOptions options) {
                AtomicBoolean first = new AtomicBoolean();
                return Flux.usingWhen(Mono.fromCallable(() -> usage.begin(blueprint, context)).subscribeOn(scheduler),
                        call -> Flux.defer(() -> call.getExpiresAt().isAfter(LocalDateTime.now(ZoneOffset.UTC))
                                        ? delegate.stream(messages, tools, singleAttempt(call, options))
                                        : Flux.error(new TimeoutException()))
                                .timeout(properties.idleTimeout(), Flux.error(new AgentRuntimeException(
                                        CreativeErrorCode.MODEL_IDLE_TIMEOUT, CreativeExecutionFailure.IDLE)), streamTimer)
                                .takeUntilOther(deadline(call))
                                .doOnNext(response -> {
                                    if (first.compareAndSet(false, true)) log.info("Model first data callId={} stepId={} epoch={} durationMs={}",
                                            call.getCallId(), context.stepId(), context.ownerEpoch(),
                                            Duration.between(call.getStartedAt(), LocalDateTime.now(ZoneOffset.UTC)).toMillis());
                                    ModelUsageService.observe(call, response.getUsage());
                                })
                                .onBackpressureBuffer(512),
                        call -> close(call, ModelCallStatus.SUCCEEDED, null),
                        (call, error) -> close(call, ModelCallStatus.FAILED, error),
                        call -> close(call, ModelCallStatus.CANCELLED, null));
            }
        };
    }

    /** 同时约束 SDK 自带计时与重试，避免下层默认值提前结束或重复计费。 */
    private GenerateOptions singleAttempt(ModelCallLedger call, GenerateOptions options) {
        Duration remaining = Duration.between(LocalDateTime.now(ZoneOffset.UTC), call.getExpiresAt());
        GenerateOptions enforced = GenerateOptions.builder().executionConfig(ExecutionConfig.builder()
                .maxAttempts(1).timeout(remaining).build()).build();
        return GenerateOptions.mergeOptions(enforced, options);
    }

    /** 账本关闭在受控阻塞线程池中完成；数据库失败保留 STARTED 等待超时标记 UNKNOWN。 */
    private Mono<Void> close(ModelCallLedger call, ModelCallStatus status, Throwable failure) {
        log.info("Model call finished callId={} stepId={} epoch={} status={} durationMs={} failureType={}",
                call.getCallId(), call.getStepId(), call.getOwnerEpoch(), status,
                Duration.between(call.getStartedAt(), LocalDateTime.now(ZoneOffset.UTC)).toMillis(),
                Objects.isNull(failure) ? null : failure.getClass().getSimpleName());
        return Mono.fromRunnable(() -> usage.finish(call, status, failure)).subscribeOn(scheduler).then();
    }

    /** 使用 Manga 定时器精确中断，普通阻塞 Scheduler 不承担定时能力。 */
    private Mono<Void> deadline(ModelCallLedger call) {
        Duration remaining = Duration.between(LocalDateTime.now(ZoneOffset.UTC), call.getExpiresAt());
        return Mono.delay(remaining.isNegative() ? Duration.ZERO : remaining, streamTimer)
                .flatMap(ignored -> Mono.error(new AgentRuntimeException(CreativeErrorCode.MODEL_TIMEOUT, CreativeExecutionFailure.TIMEOUT)));
    }
}
