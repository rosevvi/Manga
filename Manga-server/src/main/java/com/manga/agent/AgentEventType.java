package com.manga.agent;

/** 对前端开放的助手事件类型。 */
public enum AgentEventType {
    RUN_STARTED,
    CONTENT,
    TOOL_CALL_STARTED,
    TOOL_CALL_FINISHED,
    RUN_COMPLETED,
    RUN_FAILED,
    RUN_CANCELLED
}
