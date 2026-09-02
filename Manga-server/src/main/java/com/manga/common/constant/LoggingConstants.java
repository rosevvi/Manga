package com.manga.common.constant;

import java.util.regex.Pattern;

/**
 * 统一维护链路日志使用的请求头、MDC Key 和校验规则。
 */
public final class LoggingConstants {

    /** 链路标识请求头名称。 */
    public static final String TRACE_ID_HEADER = "X-Trace-Id";
    /** 链路标识 MDC 键名。 */
    public static final String TRACE_ID_MDC_KEY = "traceId";
    /** 链路标识请求属性名称。 */
    public static final String TRACE_ID_REQUEST_ATTRIBUTE = LoggingConstants.class.getName() + ".traceId";
    /** 链路标识最大长度。 */
    public static final int TRACE_ID_MAX_LENGTH = 64;
    /** 合法链路标识格式。 */
    public static final Pattern TRACE_ID_PATTERN = Pattern.compile("[A-Za-z0-9._-]+");

    /** 禁止实例化常量类。 */
    private LoggingConstants() {
    }
}
