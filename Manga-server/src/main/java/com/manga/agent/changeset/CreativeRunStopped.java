package com.manga.agent.changeset;

/** 在运行事务内通知提案聚合撤销未决审批。 */
public record CreativeRunStopped(
        /** 已锁定并停止或取消的运行标识。 */ String runId
) { }
