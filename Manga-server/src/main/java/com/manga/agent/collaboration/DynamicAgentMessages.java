package com.manga.agent.collaboration;

/** 临时规格拒绝时的脱敏说明，不返回 Prompt 正文。 */
public final class DynamicAgentMessages {
    /** 敏感能力或绕权指令被拦截。 */ public static final String REJECTED = "临时 Agent 仅允许受控只读角色和现有能力，禁止任意连接、代码执行或绕过审批";
    /** 数量、上下文或轮数预算已耗尽。 */ public static final String EXHAUSTED = "临时 Agent 数量、Kernel、提示词或累计推理轮数预算不足";
    private DynamicAgentMessages() { }
}
