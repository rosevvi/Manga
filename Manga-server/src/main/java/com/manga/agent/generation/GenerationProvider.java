package com.manga.agent.generation;
import com.manga.entity.GenerationJob;
/** 外部媒体供应商 SPI；每次网络调用均在数据库事务外，恢复能力须明确声明。 */
public interface GenerationProvider {
    /** 稳定路由。 */ GenerationProviderKey key();
    /** 是否可使用平台 jobId 安全重复提交。 */ boolean idempotentSubmission();
    /** 提交返回受理或同步完成结果；未知结果必须抛出 SUBMISSION_UNKNOWN。 */ GenerationSubmission submit(GenerationJob job);
    /** 查询供应商事实；无百分比时 progress 应为空。 */ GenerationSnapshot query(GenerationJob job);
    /** 请求取消；最终结果仍通过查询确认。 */ void cancel(GenerationJob job);
}
