package com.manga.agent;

import com.manga.common.enums.AiProviderType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MangaAgentModelFactoryTests {

    private final MangaAgentModelFactory factory = new MangaAgentModelFactory();

    @Test
    void supportsOnlyTheConfiguredOpenAiCompatibleProviders() {
        assertThat(factory.isOpenAiCompatible(AiProviderType.OPENAI)).isTrue();
        assertThat(factory.isOpenAiCompatible(AiProviderType.OPENAI_COMPATIBLE)).isTrue();
        assertThat(factory.isOpenAiCompatible(AiProviderType.DEEPSEEK)).isTrue();
        assertThat(factory.isOpenAiCompatible(AiProviderType.NEWAPI)).isTrue();
        assertThat(factory.isOpenAiCompatible(AiProviderType.ANTHROPIC)).isFalse();
        assertThat(factory.isOpenAiCompatible(AiProviderType.GEMINI)).isFalse();
        assertThat(factory.isOpenAiCompatible(AiProviderType.OLLAMA)).isFalse();
    }
}
