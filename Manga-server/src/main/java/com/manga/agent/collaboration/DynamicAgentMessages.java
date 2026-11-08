package com.manga.agent.collaboration;

/** 临时规格拒绝时的脱敏说明，不返回 Prompt 正文。 */
public final class DynamicAgentMessages {
    /** 敏感能力或绕权指令被拦截。 */ public static final String REJECTED = "临时 Agent 仅允许受控只读角色和现有能力，禁止任意连接、代码执行或绕过审批";
    /** 缺少费用估值时不允许无预算运行。 */ public static final String PRICE_REQUIRED = "请先配置 dynamic-agent.estimated-usd-per-turn 规划费用估值";
    /** 数量、上下文或费用预算已耗尽。 */ public static final String EXHAUSTED = "临时 Agent 数量、Kernel、提示词或规划费用预算不足";
    private DynamicAgentMessages() { }
}
