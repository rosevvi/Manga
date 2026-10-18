package com.manga.agent.durable;

import com.manga.agent.application.CancelCreativeRunCommand;
import com.manga.agent.application.CreativeRunCancellationService;
import com.manga.agent.application.CreativeRunStartResult;
import com.manga.agent.application.CreativeRuntime;
import com.manga.agent.application.RunAdmissionService;
import com.manga.agent.application.StartCreativeRunCommand;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 对外暴露持久化 Creative Runtime 门面，并将职责委派给应用服务。
 */
@Service
@RequiredArgsConstructor
public class DurableCreativeRuntime implements CreativeRuntime {

    private final RunAdmissionService admissionService;
    private final CreativeRunCancellationService cancellationService;

    /** 受理请求并创建可立即调度的根 Step。 */
    @Override
    public CreativeRunStartResult start(StartCreativeRunCommand command) {
        return admissionService.admit(command);
    }

    /** 幂等请求取消 Run 及其全部非终态 Step。 */
    @Override
    public void cancel(CancelCreativeRunCommand command) {
        cancellationService.cancel(command);
    }
}
