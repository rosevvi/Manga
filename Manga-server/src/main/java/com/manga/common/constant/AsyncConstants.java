package com.manga.common.constant;

/**
 * 统一维护应用异步执行器的 Bean 名称。
 */
public final class AsyncConstants {

    /** Manga 通用业务线程池 Bean 名称。 */
    public static final String MANGA_COMMON_TASK_EXECUTOR = "mangaCommonTaskExecutor";
    /** 长时间模型执行使用的独立有界线程池。 */
    public static final String MANGA_CREATIVE_TASK_EXECUTOR = "mangaCreativeTaskExecutor";
    /** 创作执行线程的可检索名称前缀。 */
    public static final String CREATIVE_THREAD_PREFIX = "manga-creative-";
    /** 外部媒体长请求使用的隔离线程池。 */
    public static final String MANGA_GENERATION_TASK_EXECUTOR = "mangaGenerationTaskExecutor";
    /** 媒体 worker 的可检索线程名称前缀。 */
    public static final String GENERATION_THREAD_PREFIX = "manga-generation-";
    /** Spring 默认任务执行器 Bean 名称。 */
    public static final String TASK_EXECUTOR = "taskExecutor";
    /** 创作运行时执行阻塞数据库访问时使用的 Reactor Scheduler Bean 名称。 */
    public static final String MANGA_BLOCKING_SCHEDULER = "mangaBlockingScheduler";
    /** Manga 通用定时任务调度器 Bean 名称。 */
    public static final String MANGA_COMMON_TASK_SCHEDULER = "mangaCommonTaskScheduler";
    /** 租约续签专用调度器。 */
    public static final String MANGA_LEASE_SCHEDULER = "mangaLeaseScheduler";
    /** 有界事件持久化调度器。 */
    public static final String MANGA_JOURNAL_SCHEDULER = "mangaJournalScheduler";
    /** 非阻塞流计时器。 */
    public static final String MANGA_STREAM_TIMER = "mangaStreamTimer";
    /** Spring 定时任务默认查找的调度器 Bean 名称。 */
    public static final String TASK_SCHEDULER = "taskScheduler";

    /** 禁止实例化常量类。 */
    private AsyncConstants() {
    }
}
