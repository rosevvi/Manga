package com.manga.common.logging;

import com.manga.common.constant.LoggingConstants;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * 为每个 HTTP 请求建立 traceId，并记录统一的请求完成日志。
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TraceIdFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        String traceId = resolveTraceId(request);
        long startedAt = System.nanoTime();
        MDC.put(LoggingConstants.TRACE_ID_MDC_KEY, traceId);
        response.setHeader(LoggingConstants.TRACE_ID_HEADER, traceId);
        boolean failed = false;
        log.debug("HTTP request started method={} path={} client={}",
                request.getMethod(), request.getRequestURI(), request.getRemoteAddr());
        try {
            filterChain.doFilter(request, response);
        } catch (IOException | ServletException | RuntimeException exception) {
            failed = true;
            throw exception;
        } finally {
            if (request.isAsyncStarted()) {
                log.debug("HTTP request entered asynchronous processing method={} path={}",
                        request.getMethod(), request.getRequestURI());
            } else {
                long durationMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);
                int status = failed && response.getStatus() < HttpServletResponse.SC_INTERNAL_SERVER_ERROR
                        ? HttpServletResponse.SC_INTERNAL_SERVER_ERROR
                        : response.getStatus();
                logCompletion(request, status, durationMillis);
            }
            MDC.remove(LoggingConstants.TRACE_ID_MDC_KEY);
        }
    }

    @Override
    protected boolean shouldNotFilterAsyncDispatch() {
        return false;
    }

    private String resolveTraceId(HttpServletRequest request) {
        Object existingTraceId = request.getAttribute(LoggingConstants.TRACE_ID_REQUEST_ATTRIBUTE);
        if (existingTraceId instanceof String traceId) {
            return traceId;
        }
        String candidate = request.getHeader(LoggingConstants.TRACE_ID_HEADER);
        if (StringUtils.hasText(candidate)
                && candidate.length() <= LoggingConstants.TRACE_ID_MAX_LENGTH
                && LoggingConstants.TRACE_ID_PATTERN.matcher(candidate).matches()) {
            request.setAttribute(LoggingConstants.TRACE_ID_REQUEST_ATTRIBUTE, candidate);
            return candidate;
        }
        String generatedTraceId = UUID.randomUUID().toString().replace("-", "");
        request.setAttribute(LoggingConstants.TRACE_ID_REQUEST_ATTRIBUTE, generatedTraceId);
        return generatedTraceId;
    }

    private void logCompletion(HttpServletRequest request, int status, long durationMillis) {
        String method = request.getMethod();
        String path = request.getRequestURI();
        if (status >= HttpServletResponse.SC_INTERNAL_SERVER_ERROR) {
            log.error("HTTP request completed method={} path={} status={} durationMs={}",
                    method, path, status, durationMillis);
        } else if (status >= HttpServletResponse.SC_BAD_REQUEST) {
            log.warn("HTTP request completed method={} path={} status={} durationMs={}",
                    method, path, status, durationMillis);
        } else {
            log.debug("HTTP request completed method={} path={} status={} durationMs={}",
                    method, path, status, durationMillis);
        }
    }
}
