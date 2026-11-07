package com.manga.agent.generation;

import java.time.LocalDateTime;

/** 浏览器可恢复的作业卡片，不序列化内部 Entity。 */
public record GenerationJobView(
        /** 分页主键。 */ Long id,
        /** 稳定作业标识。 */ String jobId,
        /** 来源运行。 */ String runId,
        /** 来源步骤。 */ String stepId,
        /** 费用批准提案。 */ String changeSetId,
        /** 所属项目。 */ Long projectId,
        /** 目标镜头。 */ Long shotId,
        /** 实际使用的供应商。 */ GenerationProviderKey provider,
        /** 持久化阶段。 */ GenerationJobStatus status,
        /** 真实百分比，无信息时为空。 */ Integer progress,
        /** 用户已撤销结果采用权。 */ boolean cancelRequested,
        /** 公开错误编码。 */ String errorCode,
        /** 脱敏错误摘要。 */ String errorMessage,
        /** 产物 ID。 */ Long artifactId,
        /** 平台转存地址。 */ String artifactUrl,
        /** 创建时间。 */ LocalDateTime createdAt,
        /** 供应商提交开始时间。 */ LocalDateTime submittedAt,
        /** 平台终态时间。 */ LocalDateTime finishedAt,
        /** 最近一次状态更新时间，便于发现监测停滞。 */ LocalDateTime updatedAt,
        /** 人工核对说明。 */ String reconciliationNote,
        /** 最近显式采用时间，不代表此后未被人工修改。 */ LocalDateTime adoptedAt,
        /** 最近采用的认证用户。 */ Long adoptedBy
) { }
