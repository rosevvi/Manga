package com.manga.config;

import com.manga.agent.generation.GenerationProperties;
import com.manga.common.constant.AsyncConstants;
import com.manga.common.logging.MdcTaskDecorator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import java.util.concurrent.ThreadPoolExecutor;

/** 对外媒体 IO 线程隔离；拒绝时不在提交事务线程内执行网络请求。 */
@Configuration
public class GenerationExecutorConfig {
    /** AbortPolicy 使调用方回到持久化补偿，不能使用 CallerRunsPolicy。 */
    @Bean(name = AsyncConstants.MANGA_GENERATION_TASK_EXECUTOR)
    public ThreadPoolTaskExecutor generationTaskExecutor(GenerationProperties properties, MdcTaskDecorator decorator) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(properties.workerThreads()); executor.setMaxPoolSize(properties.workerThreads());
        executor.setQueueCapacity(properties.workerQueueCapacity()); executor.setThreadNamePrefix(AsyncConstants.GENERATION_THREAD_PREFIX);
        executor.setTaskDecorator(decorator); executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(false);
        return executor;
    }
}
