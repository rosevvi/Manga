package com.manga.config;

import com.manga.common.constant.AsyncConstants;
import com.manga.common.logging.MdcTaskDecorator;
import com.manga.config.properties.AsyncExecutorProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * 配置可传播 MDC 的业务异步线程池和未捕获异常日志。
 */
@Slf4j
@Configuration
@EnableAsync
@RequiredArgsConstructor
public class AsyncConfig implements AsyncConfigurer {

    private final AsyncExecutorProperties properties;
    private final MdcTaskDecorator taskDecorator;

    /** 创建携带 MDC 上下文的应用线程池。 */
    @Bean(name = {AsyncConstants.APPLICATION_TASK_EXECUTOR, AsyncConstants.TASK_EXECUTOR})
    @Primary
    public ThreadPoolTaskExecutor applicationTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(properties.corePoolSize());
        executor.setMaxPoolSize(properties.maxPoolSize());
        executor.setQueueCapacity(properties.queueCapacity());
        executor.setKeepAliveSeconds(Math.toIntExact(properties.keepAlive().toSeconds()));
        executor.setAllowCoreThreadTimeOut(true);
        executor.setThreadNamePrefix(properties.threadNamePrefix());
        executor.setTaskDecorator(taskDecorator);
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(Math.toIntExact(properties.awaitTermination().toSeconds()));
        return executor;
    }

    /** 返回 Spring 异步任务执行器。 */
    @Override
    public Executor getAsyncExecutor() {
        return applicationTaskExecutor();
    }

    /** 返回异步任务未捕获异常处理器。 */
    @Override
    public AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler() {
        return (throwable, method, parameters) -> log.error(
                "Unhandled asynchronous method exception method={}",
                method.toGenericString(),
                throwable
        );
    }
}
