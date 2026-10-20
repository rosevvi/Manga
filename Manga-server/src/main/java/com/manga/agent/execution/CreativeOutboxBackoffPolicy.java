package com.manga.agent.execution;

import com.manga.agent.config.CreativeExecutionProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * 计算 Dispatch Outbox 的有上限指数退避。
 */
@Component
@RequiredArgsConstructor
public class CreativeOutboxBackoffPolicy {

    private final CreativeExecutionProperties properties;

    /** 按已经发生的尝试次数计算下一次退避时长。 */
    public Duration delay(int attempts) {
        int exponent = Math.max(0, Math.min(attempts - 1, 20));
        long multiplier = 1L << exponent;
        Duration candidate;
        try {
            candidate = properties.outboxRetryBase().multipliedBy(multiplier);
        } catch (ArithmeticException exception) {
            return properties.outboxRetryMax();
        }
        return candidate.compareTo(properties.outboxRetryMax()) > 0
                ? properties.outboxRetryMax() : candidate;
    }
}
