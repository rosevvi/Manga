package com.manga.agent.event;

/** 事务内发布，提交后只用于唤醒独立消息投影，不携带正文。 */
public record CreativeEventsCommitted(
        /** 已提交的运行标识。 */ String runId,
        /** 本次提交的最高事件序号。 */ long sequence
) { }
