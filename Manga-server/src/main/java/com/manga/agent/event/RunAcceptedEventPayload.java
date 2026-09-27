package com.manga.agent.event;

import com.manga.agent.domain.CreativeOperationType;
import com.manga.agent.domain.CreativeRunTriggerSource;

/**
 * 描述 Creative Run 完成幂等受理时写入的事件载荷。
 */
public record RunAcceptedEventPayload(
        /** 客户端提供的幂等请求标识。 */
        String requestId,
        /** Run 的触发来源。 */
        CreativeRunTriggerSource triggerSource,
        /** Run 需要完成的业务操作。 */
        CreativeOperationType operationType
) {
}
