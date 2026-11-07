package com.manga.agent.collaboration;

/** Step 终态所在事务内触发依赖解析，不依赖内存中的 Agent 调用栈。 */
public record CreativeStepSettled(
        /** 已取得聚合行锁的运行。 */ String runId
) { }
