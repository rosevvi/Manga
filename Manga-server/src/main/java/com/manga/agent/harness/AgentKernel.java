package com.manga.agent.harness;

import com.manga.agent.blueprint.AgentBlueprint;
import com.manga.agent.blueprint.AgentKernelKey;
import com.manga.agent.capability.AgentRuntimeContext;
import com.manga.agent.model.OwnedChatModel;
import io.agentscope.core.tool.Toolkit;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 聚合可按 Blueprint KernelKey 共享的模型、Toolkit 和 Harness 创建资源。
 */
public final class AgentKernel implements AutoCloseable {

    private final AgentBlueprint blueprint;
    private final OwnedChatModel model;
    private final Toolkit toolkit;
    private final HarnessProvider harnessProvider;
    private final AtomicBoolean closed = new AtomicBoolean();

    public AgentKernel(
            AgentBlueprint blueprint,
            OwnedChatModel model,
            Toolkit toolkit,
            HarnessProvider harnessProvider) {
        this.blueprint = Objects.requireNonNull(blueprint);
        this.model = Objects.requireNonNull(model);
        this.toolkit = Objects.requireNonNull(toolkit);
        this.harnessProvider = Objects.requireNonNull(harnessProvider);
    }

    /** 返回 Kernel 的稳定缓存键。 */
    public AgentKernelKey key() {
        return blueprint.kernelKey();
    }

    /** 为请求上下文创建独立 Harness；请求数据不会写入 Kernel 字段。 */
    public HarnessLease openHarness(AgentRuntimeContext context) {
        return harnessProvider.open(context);
    }

    /** 释放 Kernel 独占的模型和 Toolkit 外部连接，可重复调用。 */
    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        try {
            toolkit.closeMcpClients();
        } finally {
            model.close();
        }
    }
}
