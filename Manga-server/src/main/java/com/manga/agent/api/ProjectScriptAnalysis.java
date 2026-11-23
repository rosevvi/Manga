package com.manga.agent.api;

import com.manga.agent.domain.CreativeRunStatus;

import java.time.LocalDateTime;

/** 剧本工作台的解析进度和最近一次成功报告。 */
public record ProjectScriptAnalysis(
        /** 最近一次尝试，包含运行中与失败状态。 */ Attempt latestRun,
        /** 最近一次成功结果，可在下一次解析期间继续阅读。 */ Report report
) {
    /** 当前账号发起的解析运行摘要。 */
    public record Attempt(String runId, CreativeRunStatus status, String errorMessage) {
    }

    /** 持久化解析结果的可见正文与完成时间。 */
    public record Report(String runId, LocalDateTime finishedAt, String markdown) {
    }
}
