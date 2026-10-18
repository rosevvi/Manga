package com.manga.agent.harness;

/**
 * 保证同一 Agent 状态会话在集群内最多只有一个活跃 Harness。
 */
public interface SessionSerialGate {

    /**
     * 获取状态会话的独占租约。会话繁忙和 Redis 不可用均会明确失败，
     * 不允许降级为本机锁后继续调用模型。
     */
    SessionGateLease acquire(String stateSessionId);
}
