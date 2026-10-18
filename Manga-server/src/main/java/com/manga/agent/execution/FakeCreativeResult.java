package com.manga.agent.execution;

/**
 * 表示阶段一 Fake 执行器产生的可序列化结果。
 */
public record FakeCreativeResult(
        /** Fake 执行器生成的完整文本。 */
        String content
) {
}
