package com.manga.agent.profile;

import com.manga.agent.capability.CapabilityName;
import com.manga.agent.config.CreativeAgentProperties;
import com.manga.agent.domain.AgentPermissionPolicy;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 验证内置 Director Profile 的安全边界和版本化契约。
 */
class BuiltInAgentProfileSourceTests {

    private static final String PROMPT_RESOURCE = "classpath:agent/profiles/manga-director-system.md";

    @Test
    void shouldExposeOnlyPhaseTwoReadCapabilities() {
        CreativeAgentProperties properties = new CreativeAgentProperties(PROMPT_RESOURCE, 3, 9, 32768);
        BuiltInAgentProfileSource source = new BuiltInAgentProfileSource(
                properties, new DefaultResourceLoader());

        AgentProfile profile = source.require(AgentProfileKey.MANGA_DIRECTOR);

        assertThat(profile.version()).isEqualTo(3);
        assertThat(profile.maxTurns()).isEqualTo(9);
        assertThat(profile.permissionPolicy()).isEqualTo(AgentPermissionPolicy.READ_ONLY);
        assertThat(profile.allowedCapabilities())
                .containsExactlyInAnyOrderElementsOf(CapabilityName.phaseTwoReadOnlyCapabilities());
        assertThat(profile.systemPrompt()).contains("只能使用工具白名单中的只读能力");
    }
}
