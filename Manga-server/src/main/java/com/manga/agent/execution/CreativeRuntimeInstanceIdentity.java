package com.manga.agent.execution;

import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * 保存当前 JVM 生命周期内稳定且不硬编码的执行实例标识。
 */
@Component
public class CreativeRuntimeInstanceIdentity {

    /** 当前实例随机标识。 */
    private final String value = UUID.randomUUID().toString();

    /** 返回当前 JVM 的稳定实例标识。 */
    public String value() {
        return value;
    }
}
