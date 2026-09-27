package com.manga.common.constant;

/**
 * 统一维护应用异步执行器的 Bean 名称。
 */
public final class AsyncConstants {

    /** Manga 通用业务线程池 Bean 名称。 */
    public static final String MANGA_COMMON_TASK_EXECUTOR = "mangaCommonTaskExecutor";
    /** Spring 默认任务执行器 Bean 名称。 */
    public static final String TASK_EXECUTOR = "taskExecutor";
    /** 创作运行时执行阻塞数据库访问时使用的 Reactor Scheduler Bean 名称。 */
    public static final String MANGA_BLOCKING_SCHEDULER = "mangaBlockingScheduler";
    /** Manga 通用定时任务调度器 Bean 名称。 */
    public static final String MANGA_COMMON_TASK_SCHEDULER = "mangaCommonTaskScheduler";
    /** Spring 定时任务默认查找的调度器 Bean 名称。 */
    public static final String TASK_SCHEDULER = "taskScheduler";

    /** 禁止实例化常量类。 */
    private AsyncConstants() {
    }
}
