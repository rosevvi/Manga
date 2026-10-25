package com.manga.agent.state;
/** 控制内部状态会话是否继续允许写入。 */
public enum AgentStateSessionStatus {
    /** Run 尚未结束，可以写入内部状态。 */ ACTIVE,
    /** 已完成、失败或取消，禁止晚到写入。 */ RETIRED
}
