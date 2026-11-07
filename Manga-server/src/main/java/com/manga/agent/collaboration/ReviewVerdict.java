package com.manga.agent.collaboration;

/** 连贯性评审的语义结论，不等同于步骤执行状态。 */
public enum ReviewVerdict {
    /** 候选内容通过评审。 */ PASS,
    /** 步骤正常结束，但候选内容必须修订。 */ NEEDS_REPAIR
}
