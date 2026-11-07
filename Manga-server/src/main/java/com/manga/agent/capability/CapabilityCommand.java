package com.manga.agent.capability;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * 承载经过 AgentScope 协议解析后的能力输入。
 */
public record CapabilityCommand(
        /** 工具调用输入对象；无参数能力使用空对象。 */
        JsonNode input,
        /** SDK 工具调用标识，禁止从模型参数中获取。 */ String toolCallId
) {
    /** 无副作用读取工具和既有调用可省略工具标识。 */
    public CapabilityCommand(JsonNode input) { this(input, null); }
}
