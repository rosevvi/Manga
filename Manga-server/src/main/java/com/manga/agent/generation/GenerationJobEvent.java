package com.manga.agent.generation;
/** SSE 和恢复指令共用的安全作业摘要，不包含端点、凭据或 Base64。 */
public record GenerationJobEvent(
        /** 平台作业 ID。 */ String jobId,
        /** 最新作业状态。 */ GenerationJobStatus status,
        /** 供应商真实进度，无数据时为空。 */ Integer progress,
        /** 平台产物 ID，尚未转存时为空。 */ Long artifactId,
        /** 平台产物链接，不是供应商临时 URL。 */ String artifactUrl,
        /** 错误分类。 */ String errorCode,
        /** 可展示的错误说明。 */ String errorMessage
) { }
