package com.manga.agent.application;

/**
 * 描述一次创作运行取消请求。
 */
public record CancelCreativeRunCommand(
        /** 需要取消的运行标识。 */
        String runId,
        /** 发起取消请求的用户主键。 */
        long userId
) {
}
