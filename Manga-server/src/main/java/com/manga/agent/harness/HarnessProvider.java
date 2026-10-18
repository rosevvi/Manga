package com.manga.agent.harness;

import com.manga.agent.capability.AgentRuntimeContext;

/**
 * 使用 Kernel 稳定资源为请求上下文创建隔离 Harness。
 */
@FunctionalInterface
public interface HarnessProvider {

    /** 创建一次调用独占的 HarnessLease。 */
    HarnessLease open(AgentRuntimeContext context);
}
