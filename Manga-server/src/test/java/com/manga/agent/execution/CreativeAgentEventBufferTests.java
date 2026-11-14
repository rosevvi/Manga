package com.manga.agent.execution;

import io.agentscope.core.event.AgentEvent;
import io.agentscope.core.event.TextBlockDeltaEvent;
import io.agentscope.core.event.ToolResultStartEvent;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;
import reactor.test.StepVerifier;
import reactor.test.scheduler.VirtualTimeScheduler;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import reactor.core.scheduler.Schedulers;
import static org.assertj.core.api.Assertions.assertThat;

/** 验证合并时限、字节阈值、工具顺序和错误前冲刷，使用虚拟计时不依赖真实数据库速度。 */
class CreativeAgentEventBufferTests {
    /** 数据库消费被阻塞时，供应商事件仍能进入有界队列，计时资源不会执行数据库写入。 */
    @Test
    void slowPersistenceDoesNotBlockSourceConsumption() throws Exception {
        var timer = Schedulers.newSingle("test-event-timer");
        var journal = Schedulers.newSingle("test-event-journal");
        CountDownLatch writing = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicInteger received = new AtomicInteger();
        try {
            Flux<AgentEvent> source = Flux.range(0, 8).map(index -> (AgentEvent) text("x".repeat(1024)))
                    .doOnNext(event -> received.incrementAndGet());
            var finished = CreativeAgentEventBuffer.coalesce(source, timer)
                    .publishOn(journal, true, CreativeAgentEventBuffer.JOURNAL_PREFETCH)
                    .doOnNext(entry -> {
                        writing.countDown();
                        try { if (!release.await(3, TimeUnit.SECONDS)) throw new IllegalStateException("test persistence timeout"); }
                        catch (InterruptedException failure) { Thread.currentThread().interrupt(); throw new IllegalStateException(failure); }
                    }).then().toFuture();
            assertThat(writing.await(1, TimeUnit.SECONDS)).isTrue();
            assertThat(received.get()).isEqualTo(8);
            assertThat(finished.isDone()).isFalse();
            release.countDown(); finished.get(2, TimeUnit.SECONDS);
        } finally { release.countDown(); timer.dispose(); journal.dispose(); }
    }

    @Test
    void textFlushesBeforeToolAndBeforeFailure() {
        VirtualTimeScheduler timer = VirtualTimeScheduler.create();
        try {
            RuntimeException failed = new RuntimeException("test-only");
            Flux<AgentEvent> source = Flux.concat(Flux.fromIterable(List.of(text("甲"), text("乙"),
                    new ToolResultStartEvent("reply", "tool", "get_chapter_content"), text("丙"))), Flux.error(failed));
            StepVerifier.create(CreativeAgentEventBuffer.coalesce(source, timer))
                    .assertNext(entry -> assertThat(entry.delta()).isEqualTo("甲乙"))
                    .assertNext(entry -> assertThat(entry.event()).isInstanceOf(ToolResultStartEvent.class))
                    .assertNext(entry -> assertThat(entry.delta()).isEqualTo("丙"))
                    .expectErrorMatches(failure -> failure == failed).verify();
        } finally { timer.dispose(); }
    }

    @Test
    void textFlushesAtByteLimitOrTwoHundredMilliseconds() {
        VirtualTimeScheduler timer = VirtualTimeScheduler.create();
        try {
            Sinks.Many<AgentEvent> source = Sinks.many().unicast().onBackpressureBuffer();
            StepVerifier.withVirtualTime(() -> CreativeAgentEventBuffer.coalesce(source.asFlux(), timer), () -> timer, Long.MAX_VALUE)
                    .then(() -> source.tryEmitNext(text("a".repeat(1024))))
                    .assertNext(entry -> assertThat(entry.delta()).hasSize(1024))
                    .then(() -> source.tryEmitNext(text("tail")))
                    .expectNoEvent(Duration.ofMillis(199)).thenAwait(Duration.ofMillis(1))
                    .assertNext(entry -> assertThat(entry.delta()).isEqualTo("tail"))
                    .thenCancel().verify();
        } finally { timer.dispose(); }
    }

    private TextBlockDeltaEvent text(String value) { return new TextBlockDeltaEvent("reply", "block", value); }
}
