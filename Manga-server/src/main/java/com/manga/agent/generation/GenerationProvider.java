package com.manga.agent.generation;
import com.manga.entity.GenerationJob;
import com.manga.service.storage.GeneratedMediaFormat;
/** 外部媒体供应商 SPI；每次网络调用均在数据库事务外，恢复能力须明确声明。 */
public interface GenerationProvider {
    /** 稳定路由。 */ GenerationProviderKey key();
    /** 是否可使用平台 jobId 安全重复提交。 */ boolean idempotentSubmission();
    /** 提交返回受理或同步完成结果；未知结果必须抛出 SUBMISSION_UNKNOWN。 */ GenerationSubmission submit(GenerationJob job);
    /** 查询供应商事实；无百分比时 progress 应为空。 */ GenerationSnapshot query(GenerationJob job);
    /** 请求取消；最终结果仍通过查询确认。 */ void cancel(GenerationJob job);
    /** 返回物理产物格式，现有同步供应商默认生成 PNG。 */
    default GeneratedMediaFormat format() { return GeneratedMediaFormat.PNG; }
    /** 远端文件型供应商按冻结引用转存。 */
    default byte[] download(GenerationJob job) { throw new GenerationException(GenerationError.PROTOCOL); }
}
