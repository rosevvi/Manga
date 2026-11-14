package com.manga.agent.capability;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * 承载可以安全返回模型的结构化能力结果。
 */
public record CapabilityResult(
        /** 不包含密钥和内部异常细节的 JSON 结果。 */
        JsonNode data,
        /** 与模型结果分离的公开执行摘要，不包含正文或原始参数。 */ CapabilitySummary summary
) {
    /** 未声明公开摘要的能力保留旧构造方式，时间线仍展示名称和状态。 */
    public CapabilityResult(JsonNode data) { this(data, null); }
}
