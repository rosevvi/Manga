package com.manga.agent.event;

/**
 * 定义可供前端增量拼接的文本事件载荷。
 */
public record ContentDeltaEventPayload(
        /** 本次新增的文本片段。 */
        String delta,
        /** 产生文本的执行代次，旧事件可为空。 */ Long ownerEpoch,
        /** 供应商回复标识，旧事件可为空。 */ String replyId,
        /** 回复内文本块标识，旧事件可为空。 */ String blockId
) {
    /** 保持已有模拟执行器和历史事件可读取。 */
    public ContentDeltaEventPayload(String delta) { this(delta, null, null, null); }
}
