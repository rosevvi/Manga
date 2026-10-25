package com.manga.agent.domain;

/** 定义消息投影角色，工具执行不混入用户或助手正文。 */
public enum CreativeMessageRole {
    /** 用户提交的原始指令。 */ USER,
    /** 助手对用户可见的回答。 */ ASSISTANT,
    /** 工具调用的公开状态。 */ TOOL
}
