package com.manga.agent.collaboration;

/** 协作步骤失败传播策略。 */
public enum StepFailurePolicy {
    /** 失败终止当前 Run，并请求取消其他正在执行的分支。 */ FAIL_RUN
}
