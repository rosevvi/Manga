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
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * 配置可传播 MDC 的 Manga 通用业务线程池和未捕获异常日志。
 */
@Slf4j
@Configuration
@EnableAsync
@EnableScheduling
@RequiredArgsConstructor
public class AsyncConfig implements AsyncConfigurer {

    private final AsyncExecutorProperties properties;
    private final MdcTaskDecorator taskDecorator;

    /** 创建携带 MDC 上下文的 Manga 通用业务线程池。 */
    @Bean(name = {AsyncConstants.MANGA_COMMON_TASK_EXECUTOR, AsyncConstants.TASK_EXECUTOR})
    @Primary
    public ThreadPoolTaskExecutor mangaCommonTaskExecutor() {
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

    /** 将受控通用线程池适配为 Reactor 阻塞任务 Scheduler。 */
    @Bean(name = AsyncConstants.MANGA_BLOCKING_SCHEDULER, destroyMethod = "dispose")
    public Scheduler mangaBlockingScheduler() {
        return Schedulers.fromExecutor(mangaCommonTaskExecutor());
    }

    /** 创建负责 Outbox 发布和订阅恢复等周期任务的受控调度线程池。 */
    @Bean(name = {AsyncConstants.MANGA_COMMON_TASK_SCHEDULER, AsyncConstants.TASK_SCHEDULER})
    public ThreadPoolTaskScheduler mangaCommonTaskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(properties.scheduledPoolSize());
        scheduler.setThreadNamePrefix(properties.scheduledThreadNamePrefix());
        scheduler.setRemoveOnCancelPolicy(true);
        scheduler.setWaitForTasksToCompleteOnShutdown(true);
        scheduler.setAwaitTerminationSeconds(Math.toIntExact(properties.awaitTermination().toSeconds()));
        return scheduler;
    }

    /** 心跳不与 Outbox、模型和文本持久化共用执行资源。 */
    @Bean(name = AsyncConstants.MANGA_LEASE_SCHEDULER)
    public ThreadPoolTaskScheduler mangaLeaseScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(properties.scheduledPoolSize());
        scheduler.setThreadNamePrefix("manga-lease-");
        scheduler.setRemoveOnCancelPolicy(true);
        return scheduler;
    }

    /** 每次订阅串行消费，线程数与待处理任务均有界。 */
    @Bean(name = AsyncConstants.MANGA_JOURNAL_SCHEDULER, destroyMethod = "dispose")
    public Scheduler mangaJournalScheduler() {
        return Schedulers.newBoundedElastic(properties.maxPoolSize(), properties.queueCapacity(), "manga-journal");
    }

    /** 此线程只发出计时信号，禁止执行阻塞数据库操作。 */
    @Bean(name = AsyncConstants.MANGA_STREAM_TIMER, destroyMethod = "dispose")
    public Scheduler mangaStreamTimer() {
        return Schedulers.newSingle("manga-stream-timer");
    }

    /** 返回 Spring 异步任务执行器。 */
    @Override
    public Executor getAsyncExecutor() {
        return mangaCommonTaskExecutor();
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
