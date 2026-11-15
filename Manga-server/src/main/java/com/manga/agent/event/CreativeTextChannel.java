package com.manga.agent.event;

/** 分别合并与展示正文和供应商实际返回的分析文本。 */
public enum CreativeTextChannel {
    /** Agent 公开正文。 */ CONTENT,
    /** 供应商分析字段，缺失时不生成替代内容。 */ REASONING
}
