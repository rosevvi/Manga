package com.manga.agent.capability;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * 承载可以安全返回模型的结构化能力结果。
 */
public record CapabilityResult(
        /** 不包含密钥和内部异常细节的 JSON 结果。 */
        JsonNode data
) {
}
