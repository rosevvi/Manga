package com.manga.agent.harness;

import com.manga.agent.capability.AgentRuntimeContext;
import com.manga.agent.domain.CreativeRuntimeMessages;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.event.AgentEvent;
import io.agentscope.harness.agent.HarnessAgent;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 封装独立 AgentScope HarnessAgent 及其请求级 RuntimeContext。
 */
public final class AgentScopeHarnessLease implements HarnessLease {

    private final HarnessAgent harnessAgent;
    private final RuntimeContext runtimeContext;
    private final AtomicBoolean invoked = new AtomicBoolean();
    private final AtomicBoolean closed = new AtomicBoolean();

    public AgentScopeHarnessLease(HarnessAgent harnessAgent, AgentRuntimeContext context) {
        this.harnessAgent = Objects.requireNonNull(harnessAgent);
        this.runtimeContext = RuntimeContext.builder()
                .userId(Long.toString(context.userId()))
                .sessionId(context.stateSessionId())
                .put(AgentRuntimeContext.class, context)
                .build();
    }

    /** 延迟到订阅时启动 AgentScope 流，并拒绝同一 Lease 被重复调用。 */
    @Override
    public Flux<AgentEvent> stream(String instruction) {
        if (!StringUtils.hasText(instruction)) {
            return Flux.error(new IllegalArgumentException(
                    CreativeRuntimeMessages.AGENT_INVOCATION_INSTRUCTION_REQUIRED));
        }
        return Flux.defer(() -> {
            if (!invoked.compareAndSet(false, true)) {
                return Flux.error(new IllegalStateException(
                        CreativeRuntimeMessages.AGENT_HARNESS_ALREADY_INVOKED));
            }
            return harnessAgent.streamEvents(instruction, runtimeContext);
        });
    }

    /** 释放 AgentScope 会话缓存和注册钩子，可重复调用。 */
    @Override
    public void close() {
        if (closed.compareAndSet(false, true)) {
            harnessAgent.close();
        }
    }
}
