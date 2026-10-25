package com.manga.agent.execution;

/**
 * 保存 Agent Step 最终可审计的文本结果。
 */
public record AgentStepResult(
        /** 模型流产生的完整可见文本。 */ String content
) {
}
