package com.manga.agent.application;

import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * 使用 UUID 生成不依赖单机序列的创作运行标识。
 */
@Component
public class UuidCreativeIdentifierGenerator implements CreativeIdentifierGenerator {

    @Override
    public String nextRunId() {
        return nextUuid();
    }

    @Override
    public String nextConversationId() {
        return nextUuid();
    }

    /** 生成标准小写 UUID 文本。 */
    private String nextUuid() {
        return UUID.randomUUID().toString();
    }
}
