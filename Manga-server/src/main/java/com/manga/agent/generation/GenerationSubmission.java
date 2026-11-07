package com.manga.agent.generation;
import java.util.Objects;

/** 同时兼容同步图片接口和异步供应商的提交结果。 */
public record GenerationSubmission(
        /** 可查询的外部作业 ID；同步接口没有此 ID。 */ String providerJobId,
        /** 已知供应商事实，同步成功时包含待转存数据。 */ GenerationSnapshot snapshot
) {
    /** 异步受理必须提供可查询标识，同步成功可省略，标识长度受数据库契约约束。 */
    public GenerationSubmission {
        if (Objects.isNull(snapshot) || Objects.nonNull(providerJobId) && providerJobId.length() > 128
                || (Objects.nonNull(snapshot) && (snapshot.status() == GenerationJobStatus.SUBMITTED || snapshot.status() == GenerationJobStatus.RUNNING)
                && (Objects.isNull(providerJobId) || providerJobId.isBlank()))) throw new GenerationException(GenerationError.PROTOCOL);
    }
}
