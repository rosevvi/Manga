package com.manga.agent.durable;

import com.manga.agent.api.AgentRuntimeResponseCode;
import com.manga.agent.application.CancelCreativeRunCommand;
import com.manga.agent.application.CreativeRunStartResult;
import com.manga.agent.application.CreativeRuntime;
import com.manga.agent.application.StartCreativeRunCommand;
import com.manga.agent.domain.CreativeRuntimeType;
import com.manga.common.exception.BusinessException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/**
 * 为后续可恢复 Run、Step 和 Event 实现保留稳定运行时入口。
 */
@Component
public class DurableCreativeRuntime implements CreativeRuntime {

    @Override
    public CreativeRuntimeType runtimeType() {
        return CreativeRuntimeType.DURABLE;
    }

    @Override
    public CreativeRunStartResult start(StartCreativeRunCommand command) {
        throw unavailable();
    }

    @Override
    public void cancel(CancelCreativeRunCommand command) {
        throw unavailable();
    }

    private BusinessException unavailable() {
        return new BusinessException(AgentRuntimeResponseCode.DURABLE_RUNTIME_UNAVAILABLE,
                HttpStatus.SERVICE_UNAVAILABLE);
    }
}
