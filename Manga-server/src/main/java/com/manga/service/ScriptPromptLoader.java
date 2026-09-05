package com.manga.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;

/** 从 classpath 读取版本化剧本提示词。 */
@Component
@RequiredArgsConstructor
public class ScriptPromptLoader {

    private final ObjectMapper objectMapper;

    /** 加载指定提示词文件。 */
    public ScriptPromptTemplate load(String fileName) {
        try (var inputStream = new ClassPathResource("prompts/script/" + fileName).getInputStream()) {
            JsonNode root = objectMapper.readTree(inputStream);
            return new ScriptPromptTemplate(
                    root.path("id").asText(),
                    root.path("version").asText(),
                    root.path("systemPrompt").asText(),
                    root.path("userPromptTemplate").asText(),
                    root.path("responseSchema").toString(),
                    root.path("generationDefaults").path("maxTokens").asInt(4096));
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to load script prompt " + fileName, exception);
        }
    }
}
