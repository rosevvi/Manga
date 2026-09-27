package com.manga.agent.application;

/**
 * 定义创作运行时的启动与取消门面。
 */
public interface CreativeRuntime {

    /** 启动一次创作运行。 */
    CreativeRunStartResult start(StartCreativeRunCommand command);

    /** 请求取消一次创作运行。 */
    void cancel(CancelCreativeRunCommand command);
}
