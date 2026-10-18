package com.manga.agent.harness;

import com.manga.agent.capability.AgentRuntimeContext;
import io.agentscope.core.state.AgentStateStore;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 在模型调用前对 AgentStateStore 执行最小可用性探测。
 */
@Component
@RequiredArgsConstructor
public class AgentStateStorePreflight {

    private final AgentStateStore stateStore;

    /** 读取当前状态会话；存储不可用时由 FailClosed StateStore 直接中止。 */
    public void verify(AgentRuntimeContext context) {
        stateStore.exists(Long.toString(context.userId()), context.stateSessionId());
    }
}
