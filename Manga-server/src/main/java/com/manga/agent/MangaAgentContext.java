package com.manga.agent;

/** 传入 AgentScope RuntimeContext 的受限执行身份。 */
public record MangaAgentContext(long userId, Long projectId) {
}
