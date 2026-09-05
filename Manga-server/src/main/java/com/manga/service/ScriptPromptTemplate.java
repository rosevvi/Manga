package com.manga.service;

/** 描述从资源文件加载的剧本提示词模板。 */
public record ScriptPromptTemplate(
        String id,
        String version,
        String systemPrompt,
        String userPromptTemplate,
        String responseSchema,
        int maxTokens
) {
}
