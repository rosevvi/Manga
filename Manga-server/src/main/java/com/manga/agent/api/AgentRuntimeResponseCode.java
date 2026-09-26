package com.manga.agent.api;

import com.manga.common.api.ResponseCode;

/**
 * 定义 Agent 创作运行时的稳定响应码。
 */
public enum AgentRuntimeResponseCode implements ResponseCode {

    /** Durable Runtime 尚未开放。 */
    DURABLE_RUNTIME_UNAVAILABLE("DURABLE_RUNTIME_UNAVAILABLE", "可恢复创作运行时尚未启用"),

    /** 旧运行时只支持助手会话。 */
    LEGACY_RUNTIME_UNSUPPORTED_OPERATION(
            "LEGACY_RUNTIME_UNSUPPORTED_OPERATION",
            "当前运行时暂不支持该手动创作操作"
    );

    /** 稳定业务编码。 */
    private final String code;

    /** 默认客户端提示。 */
    private final String message;

    AgentRuntimeResponseCode(String code, String message) {
        this.code = code;
        this.message = message;
    }

    @Override
    public String code() {
        return code;
    }

    @Override
    public String message() {
        return message;
    }
}
