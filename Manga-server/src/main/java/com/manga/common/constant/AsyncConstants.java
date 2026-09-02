package com.manga.common.constant;

/**
 * 统一维护应用异步执行器的 Bean 名称。
 */
public final class AsyncConstants {

    /** 应用异步线程池 Bean 名称。 */
    public static final String APPLICATION_TASK_EXECUTOR = "applicationTaskExecutor";
    /** Spring 默认任务执行器 Bean 名称。 */
    public static final String TASK_EXECUTOR = "taskExecutor";

    /** 禁止实例化常量类。 */
    private AsyncConstants() {
    }
}
