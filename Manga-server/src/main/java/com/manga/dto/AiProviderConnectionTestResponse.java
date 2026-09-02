package com.manga.dto;

import java.time.Instant;

/** 返回 AI 服务连通性检测的安全摘要，不包含任何密钥信息。 */
public record AiProviderConnectionTestResponse(
        /** 上游服务是否可正常访问。 */
        boolean reachable,
        /** 上游服务 HTTP 状态码。 */
        Integer httpStatus,
        /** 结果提示信息。 */
        String message,
        /** 检测耗时，单位为毫秒。 */
        long durationMillis,
        /** 连接检测完成时间。 */
        Instant testedAt
) {
}
