package com.manga.agent.generation;
import java.util.Objects;
/** 供应商归一化事实，不伪造真实生成百分比。 */
public record GenerationSnapshot(
    /** 归一化状态。 */ GenerationJobStatus status,
    /** 真实百分比，无数据时为空。 */ Integer progress,
    /** PNG 的 Base64，落库后仅用于平台转存，不对浏览器暴露。 */ String imageBase64,
    /** 脱敏错误分类。 */ GenerationError error
) {
    /** 供应商不能直接宣布平台成功；只有转存事务可以发布 SUCCEEDED。 */
    public GenerationSnapshot {
        if (Objects.isNull(status) || status == GenerationJobStatus.SUCCEEDED
                || status == GenerationJobStatus.PENDING_SUBMISSION || status == GenerationJobStatus.SUBMITTING
                || status == GenerationJobStatus.UNKNOWN
                || Objects.nonNull(progress) && (progress < 0 || progress > 100)
                || status == GenerationJobStatus.MATERIALIZING && (Objects.isNull(imageBase64) || imageBase64.isBlank())
                || status != GenerationJobStatus.MATERIALIZING && Objects.nonNull(imageBase64)) {
            throw new GenerationException(GenerationError.PROTOCOL);
        }
    }
    /** 大图片不得因调试字符串进入日志。 */
    @Override
    public String toString() { return "GenerationSnapshot[status=" + status + ", progress=" + progress + ", imagePresent=" + Objects.nonNull(imageBase64) + "]"; }
}
