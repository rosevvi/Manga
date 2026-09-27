package com.manga.agent.api;

/**
 * 统一维护 Creative Run HTTP 和 SSE 协议中的稳定技术值。
 */
public final class CreativeApiConstants {

    /** Creative Run API 根路径。 */
    public static final String CREATIVE_RUNS_PATH = "/api/v1/creative-runs";
    /** SSE 断点恢复请求头名称。 */
    public static final String LAST_EVENT_ID_HEADER = "Last-Event-ID";
    /** Creative Run SSE 业务事件名称。 */
    public static final String CREATIVE_EVENT_NAME = "creative-event";
    /** Last-Event-ID 中 Run 和事件序号的分隔符。 */
    public static final char EVENT_ID_SEPARATOR = ':';

    /** 禁止实例化 API 常量类。 */
    private CreativeApiConstants() {
    }
}
