package com.manga.agent.operations;

import com.manga.agent.blueprint.AgentBlueprint;
import com.manga.agent.capability.AgentRuntimeContext;
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
import org.springframework.scheduling.TaskScheduler;
import java.util.Objects;
import java.util.List;
import java.util.concurrent.TimeoutException;

/** 每个 Harness 使用独立包装器；Kernel 仍独占底层模型，包装器不关闭共享客户端。 */
@Component
public class MeteredModelFactory {
    /** 单个账本调用只对应一次供应商尝试，覆盖 SDK 默认三次自动重试。 */
    private static final GenerateOptions SINGLE_ATTEMPT = GenerateOptions.builder()
            .executionConfig(ExecutionConfig.builder().maxAttempts(1).build()).build();
    private final ModelUsageService usage;
    private final Scheduler scheduler;
    private final TaskScheduler timer;

    public MeteredModelFactory(ModelUsageService usage, @Qualifier(AsyncConstants.MANGA_BLOCKING_SCHEDULER) Scheduler scheduler,
            @Qualifier(AsyncConstants.MANGA_COMMON_TASK_SCHEDULER) TaskScheduler timer) {
        this.usage = usage; this.scheduler = scheduler; this.timer = timer;
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
                return Flux.usingWhen(Mono.fromCallable(() -> usage.begin(blueprint, context)).subscribeOn(scheduler),
                        call -> Flux.defer(() -> call.getExpiresAt().isAfter(LocalDateTime.now(ZoneOffset.UTC))
                                        ? delegate.stream(messages, tools, GenerateOptions.mergeOptions(SINGLE_ATTEMPT, options))
                                        : Flux.error(new TimeoutException()))
                                .takeUntilOther(deadline(call))
                                .doOnNext(response -> ModelUsageService.observe(call, response.getUsage())),
                        call -> close(call, ModelCallStatus.SUCCEEDED, null),
                        (call, error) -> close(call, ModelCallStatus.FAILED, error),
                        call -> close(call, ModelCallStatus.CANCELLED, null));
            }
        };
    }

    /** 账本关闭在受控阻塞线程池中完成；数据库失败保留 STARTED 等待超时标记 UNKNOWN。 */
    private Mono<Void> close(ModelCallLedger call, ModelCallStatus status, Throwable failure) {
        return Mono.fromRunnable(() -> usage.finish(call, status, failure)).subscribeOn(scheduler).then();
    }

    /** 使用 Manga 定时器精确中断，普通阻塞 Scheduler 不承担定时能力。 */
    private Mono<Void> deadline(ModelCallLedger call) {
        return Mono.create(sink -> {
            var future = timer.schedule(() -> sink.error(new TimeoutException()), call.getExpiresAt().toInstant(ZoneOffset.UTC));
            sink.onDispose(() -> { if (Objects.nonNull(future)) future.cancel(false); });
        });
    }
}
