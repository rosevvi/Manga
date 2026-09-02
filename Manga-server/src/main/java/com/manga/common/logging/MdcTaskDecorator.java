package com.manga.common.logging;

import org.slf4j.MDC;
import org.springframework.core.task.TaskDecorator;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 在线程池提交任务时复制 MDC，使 traceId 等上下文跨线程传播且不会泄漏。
 */
@Component
public class MdcTaskDecorator implements TaskDecorator {

    /** 复制当前 MDC 上下文并包装异步任务。 */
    @Override
    public Runnable decorate(Runnable runnable) {
        Map<String, String> submittingContext = MDC.getCopyOfContextMap();
        return () -> {
            Map<String, String> previousContext = MDC.getCopyOfContextMap();
            try {
                restore(submittingContext);
                runnable.run();
            } finally {
                restore(previousContext);
            }
        };
    }

    /** 恢复执行线程原有的 MDC 上下文。 */
    private void restore(Map<String, String> context) {
        if (context == null || context.isEmpty()) {
            MDC.clear();
            return;
        }
        MDC.setContextMap(context);
    }
}
