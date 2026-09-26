package com.manga.agent;

/** 传入 AgentScope RuntimeContext 的受限执行身份。 */
public record MangaAgentContext(
        /** 当前认证用户主键。 */
        long userId,
        /** 当前运行绑定的项目主键，未绑定项目时为空。 */
        Long projectId
) {
}
