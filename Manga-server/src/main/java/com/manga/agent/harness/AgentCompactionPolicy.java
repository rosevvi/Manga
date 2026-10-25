package com.manga.agent.harness;

import io.agentscope.harness.agent.memory.compaction.CompactionConfig;

/** 从冻结的模型窗口派生单次 Harness 的压缩预算，不启用文件卸载或长期记忆写入。 */
public final class AgentCompactionPolicy {
    /** 窗口预算的分母，四分之一用于保留近期内容及生成余量。 */
    private static final int WINDOW_PARTS = 4;
    /** 达到窗口四分之三时执行语义压缩。 */
    private static final int TRIGGER_PARTS = 3;

    /** 按模型窗口生成隔离 Harness 的确定性压缩配置。 */
    public static CompactionConfig forContextWindow(int contextWindow) {
        int tailTokens = contextWindow / WINDOW_PARTS;
        return CompactionConfig.builder()
                .triggerTokens(tailTokens * TRIGGER_PARTS)
                .triggerMessages(0)
                .keepTokens(tailTokens)
                .flushBeforeCompact(false)
                .offloadBeforeCompact(false)
                .prune(null)
                .build();
    }

    /** 禁止实例化配置策略类。 */
    private AgentCompactionPolicy() { }
}
