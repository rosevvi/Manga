package com.manga.agent.execution;

import io.agentscope.core.event.AgentEvent;
import io.agentscope.core.event.TextBlockDeltaEvent;
import io.agentscope.core.event.ToolResultStartEvent;
import io.agentscope.core.event.ToolResultEndEvent;
import io.agentscope.core.event.AgentResultEvent;
import io.agentscope.core.event.ExceedMaxItersEvent;
import io.agentscope.core.event.ThinkingBlockDeltaEvent;
import com.manga.agent.event.CreativeTextChannel;
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
    private CreativeAgentEventBuffer() { }

    /** 工具和终态先冲刷文本；队列满时明确报错，取消会停止上游并释放模型资源。 */
    public static Flux<Entry> coalesce(Flux<AgentEvent> source, Scheduler timer) {
        return Flux.create(sink -> {
            Buffer buffer = new Buffer(sink, timer);
            Disposable.Swap upstream = Disposables.swap();
            sink.onDispose(() -> { upstream.dispose(); buffer.cancel(); });
            // 必须在订阅入口过滤；内部流事件既不占写入队列，也不触发正文冲刷。
            upstream.update(source.filter(CreativeAgentEventBuffer::isJournalEvent)
                    .subscribe(buffer::next, buffer::error, buffer::complete));
        }, FluxSink.OverflowStrategy.ERROR);
    }

    /** 正文、供应商分析和工具事实进入 Journal，工具参数增量仍在入口过滤。 */
    private static boolean isJournalEvent(AgentEvent event) {
        return event instanceof TextBlockDeltaEvent || event instanceof ThinkingBlockDeltaEvent || event instanceof ToolResultStartEvent
                || event instanceof ToolResultEndEvent || event instanceof AgentResultEvent
                || event instanceof ExceedMaxItersEvent;
    }

    /** 文本增量和非文本事件二选一，均只包含 Agent 公开事件。 */
    public record Entry(/** 合并的正文增量，非文本时为空。 */ String delta,
            /** 原始非文本事件，文本时为空。 */ AgentEvent event,
            /** 正文或供应商分析通道。 */ CreativeTextChannel channel,
            /** 文本所属回复。 */ String replyId,
            /** 回复内文本块。 */ String blockId,
            /** 进入有界队列的单调时钟，用于排队指标。 */ long queuedAtNanos) { }

    /** 锁只保护内存排序；sink 下游必须先 publishOn 再执行阻塞写入。 */
    private static final class Buffer {
        private final FluxSink<Entry> sink;
        private final Scheduler timer;
        private final StringBuilder pending = new StringBuilder();
        private Disposable scheduled;
        private int bytes;
        private boolean stopped;
        private CreativeTextChannel channel;
        private String replyId;
        private String blockId;
        private Buffer(FluxSink<Entry> sink, Scheduler timer) { this.sink = sink; this.timer = timer; }

        private synchronized void next(AgentEvent event) {
            if (stopped || sink.isCancelled()) return;
            if (event instanceof TextBlockDeltaEvent || event instanceof ThinkingBlockDeltaEvent) {
                boolean reasoning = event instanceof ThinkingBlockDeltaEvent;
                String delta = reasoning ? ((ThinkingBlockDeltaEvent) event).getDelta() : ((TextBlockDeltaEvent) event).getDelta();
                String nextReply = reasoning ? ((ThinkingBlockDeltaEvent) event).getReplyId() : ((TextBlockDeltaEvent) event).getReplyId();
                String nextBlock = reasoning ? ((ThinkingBlockDeltaEvent) event).getBlockId() : ((TextBlockDeltaEvent) event).getBlockId();
                CreativeTextChannel nextChannel = reasoning ? CreativeTextChannel.REASONING : CreativeTextChannel.CONTENT;
                if (Objects.isNull(delta)) { return; }
                if (channel != nextChannel || !Objects.equals(replyId, nextReply) || !Objects.equals(blockId, nextBlock)) { flush(); }
                channel = nextChannel; replyId = nextReply; blockId = nextBlock;
                pending.append(delta);
                bytes += delta.getBytes(StandardCharsets.UTF_8).length;
                if (bytes >= MAX_TEXT_BYTES) flush();
                else if (Objects.isNull(scheduled)) scheduled = timer.schedule(this::flush, FLUSH_MILLIS, TimeUnit.MILLISECONDS);
            } else {
                flush();
                sink.next(new Entry(null, event, null, null, null, System.nanoTime()));
            }
        }

        private synchronized void flush() {
            if (Objects.nonNull(scheduled)) { scheduled.dispose(); scheduled = null; }
            if (!pending.isEmpty() && !sink.isCancelled()) sink.next(new Entry(pending.toString(), null,
                    channel, replyId, blockId, System.nanoTime()));
            pending.setLength(0); bytes = 0;
        }
        private synchronized void error(Throwable failure) { if (!stopped) { flush(); stopped = true; sink.error(failure); } }
        private synchronized void complete() { if (!stopped) { flush(); stopped = true; sink.complete(); } }
        private synchronized void cancel() { stopped = true; if (Objects.nonNull(scheduled)) scheduled.dispose(); pending.setLength(0); }
    }
}
