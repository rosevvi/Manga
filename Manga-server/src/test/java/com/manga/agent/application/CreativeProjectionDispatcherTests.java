package com.manga.agent.application;

import org.junit.jupiter.api.Test;
import reactor.core.scheduler.Scheduler;
import java.util.concurrent.RejectedExecutionException;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** 消息投影队列饱和只延迟派生视图，不向事件提交或模型执行传播失败。 */
class CreativeProjectionDispatcherTests {
    @Test
    void rejectedWakeupLeavesJournalAndModelUntouched() {
        var projector = mock(CreativeMessageProjector.class);
        var scheduler = mock(Scheduler.class);
        when(scheduler.schedule(any(Runnable.class))).thenThrow(new RejectedExecutionException("test saturation"));
        var dispatcher = new CreativeProjectionDispatcher(projector, scheduler);
        assertThatCode(() -> dispatcher.wake("run-under-load")).doesNotThrowAnyException();
        assertThatCode(() -> dispatcher.wake("run-under-load")).doesNotThrowAnyException();
        verifyNoInteractions(projector);
    }
}
