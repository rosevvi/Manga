package com.manga.agent.harness;

import io.agentscope.core.event.AgentEvent;
import reactor.core.publisher.Flux;

/**
 * 表示一次隔离 Harness 的唯一执行权及其释放边界。
 */
public interface HarnessLease extends AutoCloseable {

    /** 以流式 AgentEvent 执行一次且仅一次调用。 */
    Flux<AgentEvent> stream(String instruction);

    /** 释放 Harness 的会话缓存和钩子资源，可重复调用。 */
    @Override
    void close();
}
