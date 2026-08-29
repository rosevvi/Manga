package com.manga.common.logging;

import com.manga.common.constant.LoggingConstants;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 验证异步任务执行前后正确传播并恢复 MDC 上下文。
 */
class MdcTaskDecoratorTests {

    private final MdcTaskDecorator taskDecorator = new MdcTaskDecorator();

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void shouldPropagateTraceIdAndRestoreWorkerContext() {
        MDC.put(LoggingConstants.TRACE_ID_MDC_KEY, "request-trace-id");
        AtomicReference<String> asynchronousTraceId = new AtomicReference<>();
        Runnable decoratedTask = taskDecorator.decorate(() -> asynchronousTraceId.set(
                MDC.get(LoggingConstants.TRACE_ID_MDC_KEY)
        ));

        MDC.put(LoggingConstants.TRACE_ID_MDC_KEY, "worker-previous-trace-id");
        decoratedTask.run();

        assertThat(asynchronousTraceId).hasValue("request-trace-id");
        assertThat(MDC.get(LoggingConstants.TRACE_ID_MDC_KEY)).isEqualTo("worker-previous-trace-id");
    }
}
