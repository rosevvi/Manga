package com.manga.agent.execution;

import io.agentscope.core.event.AgentEvent;
import io.agentscope.core.event.TextBlockDeltaEvent;
import reactor.core.Disposable;
import reactor.core.Disposables;
import reactor.core.publisher.Flux;
import reactor.core.publisher.FluxSink;
import reactor.core.scheduler.Scheduler;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/** 在供应商消费与数据库之间合并连续文本，计时线程只投递事实，绝不执行数据库操作。 */
public final class CreativeAgentEventBuffer {
    /** 文本达到此 UTF-8 字节数即投递。 */
    private static final int MAX_TEXT_BYTES = 1024;
    /** 首个未提交文本片段最多等待毫秒数。 */
    private static final long FLUSH_MILLIS = 200;
    /** 数据库消费队列容量，每项为一段合并文本或一个工具事实。 */
    public static final int JOURNAL_PREFETCH = 32;
    private CreativeAgentEventBuffer() { }

    /** 工具和终态先冲刷文本；队列满时明确报错，取消会停止上游并释放模型资源。 */
    public static Flux<Entry> coalesce(Flux<AgentEvent> source, Scheduler timer) {
        return Flux.create(sink -> {
            Buffer buffer = new Buffer(sink, timer);
            Disposable.Swap upstream = Disposables.swap();
            sink.onDispose(() -> { upstream.dispose(); buffer.cancel(); });
            upstream.update(source.subscribe(buffer::next, buffer::error, buffer::complete));
        }, FluxSink.OverflowStrategy.ERROR);
    }

    /** 文本增量和非文本事件二选一，均只包含 Agent 公开事件。 */
    public record Entry(/** 合并的正文增量，非文本时为空。 */ String delta,
            /** 原始非文本事件，文本时为空。 */ AgentEvent event) { }

    /** 锁只保护内存排序；sink 下游必须先 publishOn 再执行阻塞写入。 */
    private static final class Buffer {
        private final FluxSink<Entry> sink;
        private final Scheduler timer;
        private final StringBuilder pending = new StringBuilder();
        private Disposable scheduled;
        private int bytes;
        private boolean stopped;
        private Buffer(FluxSink<Entry> sink, Scheduler timer) { this.sink = sink; this.timer = timer; }

        private synchronized void next(AgentEvent event) {
            if (stopped || sink.isCancelled()) return;
            if (event instanceof TextBlockDeltaEvent text && Objects.nonNull(text.getDelta())) {
                pending.append(text.getDelta());
                bytes += text.getDelta().getBytes(StandardCharsets.UTF_8).length;
                if (bytes >= MAX_TEXT_BYTES) flush();
                else if (Objects.isNull(scheduled)) scheduled = timer.schedule(this::flush, FLUSH_MILLIS, TimeUnit.MILLISECONDS);
            } else {
                flush();
                sink.next(new Entry(null, event));
            }
        }

        private synchronized void flush() {
            if (Objects.nonNull(scheduled)) { scheduled.dispose(); scheduled = null; }
            if (!pending.isEmpty() && !sink.isCancelled()) sink.next(new Entry(pending.toString(), null));
            pending.setLength(0); bytes = 0;
        }
        private synchronized void error(Throwable failure) { if (!stopped) { flush(); stopped = true; sink.error(failure); } }
        private synchronized void complete() { if (!stopped) { flush(); stopped = true; sink.complete(); } }
        private synchronized void cancel() { stopped = true; if (Objects.nonNull(scheduled)) scheduled.dispose(); pending.setLength(0); }
    }
}
