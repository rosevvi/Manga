package com.manga.agent.profile;

import com.manga.agent.domain.CreativeOperationType;
import com.manga.agent.domain.CreativeResourceType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 验证业务操作到 Agent Profile 的显式路由契约。
 */
class AgentOperationBindingTests {

    @Test
    void shouldRouteScriptParseToReadOnlyScriptParser() {
        AgentOperationBinding binding = AgentOperationBinding.find(CreativeOperationType.PARSE_SCRIPT)
                .orElseThrow();

        assertThat(binding.profileKey()).isEqualTo(AgentProfileKey.SCRIPT_PARSER);
        assertThat(binding.targetType()).isEqualTo(CreativeResourceType.SCRIPT);
        assertThat(AgentOperationBinding.find(CreativeOperationType.ASSISTANT_CHAT).orElseThrow().profileKey())
                .isEqualTo(AgentProfileKey.MANGA_DIRECTOR);
    }
}
