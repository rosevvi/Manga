package com.manga.common.constant;

import java.util.regex.Pattern;

/**
 * 统一维护链路日志使用的请求头、MDC Key 和校验规则。
 */
public final class LoggingConstants {

    public static final String TRACE_ID_HEADER = "X-Trace-Id";
    public static final String TRACE_ID_MDC_KEY = "traceId";
    public static final String TRACE_ID_REQUEST_ATTRIBUTE = LoggingConstants.class.getName() + ".traceId";
    public static final int TRACE_ID_MAX_LENGTH = 64;
    public static final Pattern TRACE_ID_PATTERN = Pattern.compile("[A-Za-z0-9._-]+");

    private LoggingConstants() {
    }
}
