package com.manga.agent.generation;
import java.util.Objects;
/** 供应商归一化事实，不伪造真实生成百分比。 */
public record GenerationSnapshot(
    /** 归一化状态。 */ GenerationJobStatus status,
    /** 真实百分比，无数据时为空。 */ Integer progress,
    /** PNG 的 Base64，落库后仅用于平台转存，不对浏览器暴露。 */ String imageBase64,
    /** 脱敏错误分类。 */ GenerationError error,
    /** 视频转存地址，仅供 worker 使用。 */ String resultUrl
) {
    /** 保留图片及通用状态构造方式。 */
    public GenerationSnapshot(GenerationJobStatus status, Integer progress, String imageBase64, GenerationError error) {
        this(status, progress, imageBase64, error, null);
    }
    /** 供应商不能直接宣布平台成功；只有转存事务可以发布 SUCCEEDED。 */
    public GenerationSnapshot {
        if (Objects.isNull(status) || status == GenerationJobStatus.SUCCEEDED
                || status == GenerationJobStatus.PENDING_SUBMISSION || status == GenerationJobStatus.SUBMITTING
                || status == GenerationJobStatus.UNKNOWN
                || Objects.nonNull(progress) && (progress < 0 || progress > 100)
                || status == GenerationJobStatus.MATERIALIZING
                    && ((imageBase64 == null || imageBase64.isBlank()) == (resultUrl == null || resultUrl.isBlank()))
                || status != GenerationJobStatus.MATERIALIZING && (imageBase64 != null || resultUrl != null)) {
            throw new GenerationException(GenerationError.PROTOCOL);
        }
    }
    /** 大图片不得因调试字符串进入日志。 */
    @Override
    public String toString() { return "GenerationSnapshot[status=" + status + ", progress=" + progress + ", imagePresent=" + Objects.nonNull(imageBase64) + "]"; }
}
