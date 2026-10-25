package com.manga.agent.application;

/**
 * 统一生成创作运行和会话使用的随机标识。
 */
public interface CreativeIdentifierGenerator {

    /** 生成新的 Run 标识。 */
    String nextRunId();

    /** 生成新的助手会话标识。 */
    String nextConversationId();

    /** 生成新的 Step 标识。 */
    String nextStepId();

    /** 生成新的 AgentScope 状态会话标识。 */
    String nextStateSessionId();

}
