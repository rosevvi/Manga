package com.manga.agent.domain;

/**
 * 固化根 Step 执行所需的最小输入，避免持久化代码使用魔法键 Map。
 */
public record CreativeStepInput(
        /** 用户的自然语言指令或手动操作说明。 */
        String instruction,
        /** 本次运行的稳定业务操作。 */
        CreativeOperationType operationType,
        /** 可选的直接目标资源。 */
        CreativeResourceReference target
) {
}
