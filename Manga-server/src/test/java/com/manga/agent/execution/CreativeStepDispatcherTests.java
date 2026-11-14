package com.manga.agent.execution;

import com.manga.agent.domain.AgentPermissionPolicy;
import com.manga.agent.domain.CreativeStepType;
import org.junit.jupiter.api.Test;
import java.time.LocalDateTime;
import java.util.Optional;
import static org.mockito.Mockito.*;

/** 验证异常提交永远使用领取时的代次，迟到执行器不能误结束后来所有者。 */
class CreativeStepDispatcherTests {
    @Test
    void lateWorkerCannotUseNewEpochToFailStep() {
        var claims = mock(CreativeStepClaimService.class);
        var registry = mock(CreativeStepExecutorRegistry.class);
        var lifecycle = mock(CreativeStepLifecycleService.class);
        var executor = mock(CreativeStepExecutor.class);
        var original = new ClaimedCreativeStep("step", "run", CreativeStepType.AGENT_INVOCATION, "same-process", 1L,
                "{}", 1L, null, null, LocalDateTime.now().plusMinutes(10), AgentPermissionPolicy.READ_ONLY, "{}", "hash", "session");
        when(claims.claim("step", 1L)).thenReturn(Optional.of(original));
        when(registry.require(original.stepType())).thenReturn(executor);
        RuntimeException failure = new RuntimeException("test-late-worker");
        doThrow(failure).when(executor).execute(original);
        var dispatcher = new CreativeStepDispatcher(claims, registry, lifecycle, Runnable::run);
        dispatcher.dispatchAsync("step", 1L);
        verify(lifecycle).fail(original, failure);
        verify(claims).claim("step", 1L);
        verifyNoMoreInteractions(claims);
    }
}
