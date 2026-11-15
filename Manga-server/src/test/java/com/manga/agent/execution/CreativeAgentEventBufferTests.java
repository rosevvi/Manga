package com.manga.agent.execution;

import com.manga.agent.event.CreativeTextChannel;

import com.manga.agent.config.CreativeJournalProperties;
import io.agentscope.core.event.AgentEvent;
import io.agentscope.core.event.TextBlockDeltaEvent;
import io.agentscope.core.event.ToolResultStartEvent;
import io.agentscope.core.event.ToolResultEndEvent;
import io.agentscope.core.event.ThinkingBlockDeltaEvent;
import io.agentscope.core.event.ToolCallDeltaEvent;
import io.agentscope.core.message.ToolResultState;
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
    private final CreativeJournalProperties journalProperties = new CreativeJournalProperties(4096, 16, 2048);
    /** 供应商分析和正文按通道及回复块分开，工具参数仍不会打断正文合并。 */
    @Test
    void reasoningAndReplyBlocksHaveSeparateOrderedEntries() {
        var timer = VirtualTimeScheduler.create();
        try {
            StepVerifier.create(CreativeAgentEventBuffer.coalesce(Flux.just(
                    new ThinkingBlockDeltaEvent("reply", "think", "供应商分析"),
                    new ThinkingBlockDeltaEvent("reply", "think", "继续分析"), text("公开正文"),
                    new TextBlockDeltaEvent("reply", "another-block", "另一个块")), timer))
                    .assertNext(entry -> {
                        assertThat(entry.channel()).isEqualTo(CreativeTextChannel.REASONING);
                        assertThat(entry.delta()).isEqualTo("供应商分析继续分析");
                    }).assertNext(entry -> assertThat(entry.delta()).isEqualTo("公开正文"))
                    .assertNext(entry -> assertThat(entry.blockId()).isEqualTo("another-block"))
                    .verifyComplete();
        } finally { timer.dispose(); }
    }
    /** 复现工具落库阻塞期间大量工具参数增量挤满队列；过滤后只持久化真实业务事实。 */
    @Test
    void internalEventsCannotOverflowBlockedJournal() throws Exception {
        var timer = Schedulers.newSingle("test-internal-timer");
        var journal = Schedulers.newSingle("test-internal-journal");
        CountDownLatch writing = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicInteger internalReceived = new AtomicInteger();
        try {
            Flux<AgentEvent> internal = Flux.range(0, 10000).map(index -> (AgentEvent) new ToolCallDeltaEvent("reply", "tool", "get_chapter_content", "parameter-fragment"))
                    .doOnNext(event -> internalReceived.incrementAndGet());
            Flux<AgentEvent> source = Flux.concat(
                    Flux.just(new ToolResultStartEvent("reply", "tool", "get_chapter_content")), internal,
                    Flux.just(new ToolResultEndEvent("reply", "tool", "get_chapter_content", ToolResultState.SUCCESS), text("公开回答")));
            var result = CreativeAgentEventBuffer.coalesce(source, timer)
                    .publishOn(journal, true, journalProperties.eventQueueCapacity())
                    .doOnNext(entry -> {
                        writing.countDown();
                        try { if (!release.await(3, TimeUnit.SECONDS)) throw new IllegalStateException("test persistence timeout"); }
                        catch (InterruptedException failure) { Thread.currentThread().interrupt(); throw new IllegalStateException(failure); }
                    }).collectList().toFuture();
            assertThat(writing.await(1, TimeUnit.SECONDS)).isTrue();
            assertThat(internalReceived.get()).isEqualTo(10000);
            assertThat(result.isDone()).isFalse();
            release.countDown();
            var entries = result.get(2, TimeUnit.SECONDS);
            assertThat(entries).hasSize(3);
            assertThat(entries.get(0).event()).isInstanceOf(ToolResultStartEvent.class);
            assertThat(entries.get(1).event()).isInstanceOf(ToolResultEndEvent.class);
            assertThat(entries.get(2).delta()).isEqualTo("公开回答");
        } finally { release.countDown(); timer.dispose(); journal.dispose(); }
    }

    /** 内部事件不会把一段连续正文拆成大量小事务，也不会公开工具参数。 */
    @Test
    void internalEventsDoNotFlushPublicText() {
        VirtualTimeScheduler timer = VirtualTimeScheduler.create();
        try {
            Flux<AgentEvent> source = Flux.just(text("甲"),
                    new ToolCallDeltaEvent("reply", "tool", "get_chapter_content", "parameter-fragment"), text("乙"));
            StepVerifier.create(CreativeAgentEventBuffer.coalesce(source, timer))
                    .assertNext(entry -> assertThat(entry.delta()).isEqualTo("甲乙"))
                    .verifyComplete();
        } finally { timer.dispose(); }
    }

    /** 数据库消费被阻塞时可接收完整配置容量的正文，释放后按序消费，不再受原 32 项限制。 */
    @Test
    void slowPersistenceDoesNotBlockSourceConsumption() throws Exception {
        var timer = Schedulers.newSingle("test-event-timer");
        var journal = Schedulers.newSingle("test-event-journal");
        CountDownLatch writing = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicInteger received = new AtomicInteger();
        try {
            int eventCount = journalProperties.eventQueueCapacity();
            Flux<AgentEvent> source = Flux.range(0, eventCount)
                    .map(index -> (AgentEvent) text(String.format("%04d", index) + "x".repeat(1024)))
                    .doOnNext(event -> received.incrementAndGet());
            var finished = CreativeAgentEventBuffer.coalesce(source, timer)
                    .publishOn(journal, true, journalProperties.eventQueueCapacity())
                    .doOnNext(entry -> {
                        writing.countDown();
                        try { if (!release.await(3, TimeUnit.SECONDS)) throw new IllegalStateException("test persistence timeout"); }
                        catch (InterruptedException failure) { Thread.currentThread().interrupt(); throw new IllegalStateException(failure); }
                    }).collectList().toFuture();
            assertThat(writing.await(1, TimeUnit.SECONDS)).isTrue();
            assertThat(received.get()).isEqualTo(eventCount);
            assertThat(finished.isDone()).isFalse();
            release.countDown();
            var entries = finished.get(2, TimeUnit.SECONDS);
            assertThat(entries).hasSize(eventCount);
            for (int index = 0; index < eventCount; index++) {
                assertThat(entries.get(index).delta()).startsWith(String.format("%04d", index));
            }
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
