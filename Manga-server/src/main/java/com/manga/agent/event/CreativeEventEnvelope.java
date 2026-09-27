package com.manga.agent.event;

import com.fasterxml.jackson.databind.JsonNode;
import com.manga.agent.domain.CreativeEventType;

import java.time.LocalDateTime;

/**
 * 定义持久化事件日志和 SSE 共用的版本化事件信封。
 */
public record CreativeEventEnvelope(
        /** Envelope Schema 版本。 */
        int schemaVersion,
        /** 所属 Run 标识。 */
        String runId,
        /** 可选的所属 Step 标识。 */
        String stepId,
        /** Run 内严格递增的事件序号。 */
        long sequence,
        /** 稳定事件类型。 */
        CreativeEventType eventType,
        /** 产生事件的运行时组件或能力。 */
        String source,
        /** 可选的工具调用、作业或审批关联标识。 */
        String correlationId,
        /** 与事件类型匹配的结构化载荷。 */
        JsonNode payload,
        /** 事件提交时间。 */
        LocalDateTime createdAt
) {
}
