package com.manga.agent.harness;

import java.time.Instant;

/**
 * 表示一个状态会话的可续期独占租约。
 */
public interface SessionGateLease extends AutoCloseable {

    /** 返回当前租约保护的状态会话标识。 */
    String stateSessionId();

    /** 返回本地观测到的当前租约截止时间。 */
    Instant expiresAt();

    /** 原子续期；租约丢失时抛出稳定运行时异常。 */
    void renew();

    /** 仅由持有相同令牌的租约释放闸门，可重复调用。 */
    @Override
    void close();
}
