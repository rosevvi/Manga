package com.manga.agent.model;

import com.manga.agent.blueprint.ModelSnapshot;
import com.manga.common.enums.AiProviderType;
import com.manga.entity.AiProviderConfig;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 验证模型适配器注册表的唯一性和封闭式路由约束。
 */
class AgentModelProviderRegistryTests {

    @Test
    void shouldRejectDuplicatedProviderType() {
        AgentChatModelProvider first = provider(AiProviderType.OPENAI);
        AgentChatModelProvider second = provider(AiProviderType.OPENAI);

        assertThatThrownBy(() -> new AgentModelProviderRegistry(List.of(first, second)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("OPENAI");
    }

    @Test
    void shouldRejectProviderTypeWithoutRegisteredAdapter() {
        AgentModelProviderRegistry registry = new AgentModelProviderRegistry(
                List.of(provider(AiProviderType.OPENAI)));
        AiProviderConfig config = AiProviderConfig.builder()
                .providerType(AiProviderType.ANTHROPIC)
                .build();
        ModelSnapshot snapshot = new ModelSnapshot(
                1L,
                AiProviderType.ANTHROPIC,
                "https://api.anthropic.com",
                "claude-test",
                "fingerprint",
                32768
        );

        assertThatThrownBy(() -> registry.create(config, snapshot, "secret", null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ANTHROPIC");
    }

    private AgentChatModelProvider provider(AiProviderType type) {
        AgentChatModelProvider provider = mock(AgentChatModelProvider.class);
        when(provider.supportedTypes()).thenReturn(Set.of(type));
        return provider;
    }
}
