package com.manga.agent.domain;

/**
 * 标识创作运行由何种用户入口触发。
 */
public enum CreativeRunTriggerSource {

    /** 用户通过创作助手会话发起。 */
    CONVERSATION,

    /** 用户通过项目页面上的明确操作发起。 */
    MANUAL
}
