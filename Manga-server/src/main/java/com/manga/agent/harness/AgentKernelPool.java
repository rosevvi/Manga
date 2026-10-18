package com.manga.agent.harness;

import com.manga.agent.blueprint.AgentBlueprint;

/**
 * 管理有界共享 Agent Kernel 及其活跃 Handle 生命周期。
 */
public interface AgentKernelPool {

    /** 获取并固定 Blueprint 对应 Kernel，容量耗尽时有界等待。 */
    KernelHandle acquire(AgentBlueprint blueprint);

    /** 停止接受新 Handle，等待活跃引用释放后关闭全部 Kernel。 */
    void drain();
}
