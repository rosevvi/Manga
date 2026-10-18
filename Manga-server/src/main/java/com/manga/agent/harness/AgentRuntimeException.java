package com.manga.agent.harness;

import com.manga.agent.domain.CreativeErrorCode;

import java.util.Objects;

/**
 * 携带稳定创作错误编码的 Agent 运行时异常。
 */
public class AgentRuntimeException extends RuntimeException {

    private final CreativeErrorCode errorCode;

    public AgentRuntimeException(CreativeErrorCode errorCode, String message) {
        super(message);
        this.errorCode = Objects.requireNonNull(errorCode);
    }

    public AgentRuntimeException(CreativeErrorCode errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = Objects.requireNonNull(errorCode);
    }

    /** 返回可持久化和对外检索的稳定错误编码。 */
    public CreativeErrorCode errorCode() {
        return errorCode;
    }
}
