package com.manga.agent.application;

import com.manga.agent.domain.CreativeRuntimeType;

/**
 * 定义创作运行时的启动与取消门面。
 */
public interface CreativeRuntime {

    /** 返回该实现对应的稳定运行时类型。 */
    CreativeRuntimeType runtimeType();

    /** 启动一次创作运行。 */
    CreativeRunStartResult start(StartCreativeRunCommand command);

    /** 请求取消一次创作运行。 */
    void cancel(CancelCreativeRunCommand command);
}
