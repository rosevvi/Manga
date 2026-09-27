package com.manga.agent.application;

import com.manga.agent.domain.CreativeRunStatus;

import java.time.LocalDateTime;

/**
 * 返回创作运行启动后的稳定摘要。
 */
public record CreativeRunStartResult(
        /** 新运行的唯一标识。 */
        String runId,
        /** 运行所属的会话标识。 */
        String conversationId,
        /** 运行绑定的项目主键。 */
        Long projectId,
        /** 运行创建后的当前状态。 */
        CreativeRunStatus status,
        /** 运行开始时间。 */
        LocalDateTime startedAt,
        /** 运行结束时间，未结束时为空。 */
        LocalDateTime finishedAt,
        /** 终态错误编码，未失败时为空。 */
        String errorCode,
        /** 脱敏后的终态错误摘要，未失败时为空。 */
        String errorMessage
) {
}
