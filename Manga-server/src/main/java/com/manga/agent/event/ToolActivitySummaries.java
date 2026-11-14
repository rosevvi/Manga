package com.manga.agent.event;

import com.manga.agent.capability.AgentRuntimeContext;
import com.manga.agent.capability.CapabilitySummary;
import org.springframework.stereotype.Component;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.LinkedHashMap;

/** 仅暂存白名单业务摘要，工具结束事件写入后即移除，不保留原始输入与结果。 */
@Component
public class ToolActivitySummaries {
    private static final int MAX_SUMMARIES = 10000;
    private static final Duration RETENTION = Duration.ofMinutes(10);
    private final LinkedHashMap<String, Summary> summaries = new LinkedHashMap<>();

    /** 只接收领域声明的类型化摘要，原始工具参数和结果根本不进入缓存。 */
    public synchronized void record(AgentRuntimeContext context, String callId, CapabilitySummary summary, LocalDateTime started) {
        summaries.entrySet().removeIf(entry -> entry.getValue().finishedAt().isBefore(LocalDateTime.now().minus(RETENTION)));
        if (summaries.size() >= MAX_SUMMARIES) summaries.pollFirstEntry();
        CapabilitySummary visible = Objects.requireNonNullElseGet(summary, () -> new CapabilitySummary("当前项目", "已完成"));
        summaries.put(key(context.runId(), context.stepId(), context.ownerEpoch(), callId),
                new Summary(visible.inputSummary(), visible.resultSummary(), started, LocalDateTime.now()));
    }

    /** 每个工具完成只消费一次；进程退出或旧事件缺失摘要时安全退回名称和状态。 */
    public synchronized Summary take(String runId, String stepId, long epoch, String callId) {
        String key = key(runId, stepId, epoch, callId);
        return summaries.remove(key);
    }

    private String key(String run, String step, long epoch, String call) { return run + ":" + step + ":" + epoch + ":" + Objects.toString(call, ""); }

    /** 可公开的工具摘要，不携带正文与供应商原始响应。 */
    public record Summary(/** 输入资源摘要。 */ String inputSummary, /** 领域结果摘要。 */ String resultSummary,
            /** 实际工具开始时间。 */ LocalDateTime startedAt, /** 实际工具结束时间。 */ LocalDateTime finishedAt) { }
}
