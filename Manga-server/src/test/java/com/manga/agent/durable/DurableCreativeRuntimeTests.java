package com.manga.agent.durable;

import com.manga.agent.application.CancelCreativeRunCommand;
import com.manga.agent.application.CreativeRunCancellationService;
import com.manga.agent.application.CreativeRunStartResult;
import com.manga.agent.application.RunAdmissionService;
import com.manga.agent.application.StartCreativeRunCommand;
import com.manga.agent.domain.CreativeRunStatus;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 验证 Durable Runtime 只承担稳定门面职责。
 */
class DurableCreativeRuntimeTests {

    /** 验证启动命令完整委派给受理服务。 */
    @Test
    void shouldDelegateAdmission() {
        RunAdmissionService admissionService = mock(RunAdmissionService.class);
        CreativeRunCancellationService cancellationService = mock(CreativeRunCancellationService.class);
        DurableCreativeRuntime runtime = new DurableCreativeRuntime(admissionService, cancellationService);
        StartCreativeRunCommand command = StartCreativeRunCommand.conversation(
                101L, "request-test", null, null, "创建漫剧项目");
        CreativeRunStartResult expected = new CreativeRunStartResult(
                "run-test", "conversation-test", null, CreativeRunStatus.QUEUED,
                null, null, null, null);
        when(admissionService.admit(command)).thenReturn(expected);

        CreativeRunStartResult actual = runtime.start(command);

        assertThat(actual).isEqualTo(expected);
        verify(admissionService).admit(command);
    }

    /** 验证取消命令完整委派给取消服务。 */
    @Test
    void shouldDelegateCancellation() {
        RunAdmissionService admissionService = mock(RunAdmissionService.class);
        CreativeRunCancellationService cancellationService = mock(CreativeRunCancellationService.class);
        DurableCreativeRuntime runtime = new DurableCreativeRuntime(admissionService, cancellationService);
        CancelCreativeRunCommand command = new CancelCreativeRunCommand("run-test", 101L);

        runtime.cancel(command);

        verify(cancellationService).cancel(command);
    }
}
