package com.manga.agent.config;

import com.manga.common.constant.AsyncConstants;
import com.manga.common.logging.MdcTaskDecorator;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import java.util.concurrent.ThreadPoolExecutor;

/** 创作执行资源隔离；拒绝时交给 Outbox，不在提交回调线程内执行模型。 */
@Configuration
@RequiredArgsConstructor
public class CreativeDispatchPoolConfiguration {
    private final CreativeDispatchPoolProperties properties;
    private final MdcTaskDecorator decorator;

    /** 创建传播 MDC 的专用有界线程池，队列饱和明确拒绝，禁止 CallerRuns。 */
    @Bean(name = AsyncConstants.MANGA_CREATIVE_TASK_EXECUTOR)
    public ThreadPoolTaskExecutor creativeTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(properties.corePoolSize());
        executor.setMaxPoolSize(properties.maxPoolSize());
        executor.setQueueCapacity(properties.queueCapacity());
        executor.setTaskDecorator(decorator);
        executor.setThreadNamePrefix(AsyncConstants.CREATIVE_THREAD_PREFIX);
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(Math.toIntExact(properties.awaitTermination().toSeconds()));
        return executor;
    }
}
