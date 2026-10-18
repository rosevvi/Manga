package com.manga.agent.blueprint;

/**
 * 同时返回类型化 Blueprint 和可写入 Step 的不可变 JSON 快照。
 */
public record CompiledAgentBlueprint(
        /** 供运行时验证和构建 Kernel 使用的类型化规格。 */
        AgentBlueprint blueprint,
        /** 创建 Step 时持久化的完整 Blueprint JSON。 */
        String snapshotJson
) {
}
