package com.manga.agent.harness;

import com.manga.agent.capability.AgentRuntimeContext;

/**
 * 固定一个共享 Kernel 生命周期，Handle 活跃时该 Kernel 不得被淘汰。
 */
public interface KernelHandle extends AutoCloseable {

    /** 为一次请求创建隔离的 Harness 执行租约。 */
    HarnessLease openHarness(AgentRuntimeContext context);

    /** 释放对 Kernel 的引用，可重复调用。 */
    @Override
    void close();
}
