package com.manga.agent.capability;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.manga.agent.domain.AgentPermissionPolicy;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 验证 Capability Registry 的精确白名单和唯一注册约束。
 */
class CapabilityRegistryTests {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void shouldReturnDescriptorsInStableMachineNameOrder() {
        CapabilityRegistry registry = new CapabilityRegistry(List.of(
                capability(CapabilityName.LIST_STORYBOARD_SHOTS),
                capability(CapabilityName.GET_PROJECT_OVERVIEW),
                capability(CapabilityName.GET_CHAPTER_CONTENT),
                capability(CapabilityName.GET_SCRIPT_STRUCTURE)
        ));

        assertThat(registry.descriptors(CapabilityName.phaseTwoReadOnlyCapabilities()))
                .extracting(descriptor -> descriptor.name().code())
                .containsExactly(
                        "get_chapter_content",
                        "get_project_overview",
                        "get_script_structure",
                        "list_storyboard_shots"
                );
    }

    @Test
    void shouldRejectDuplicateCapabilityName() {
        MangaAgentCapability first = capability(CapabilityName.GET_PROJECT_OVERVIEW);
        MangaAgentCapability duplicate = capability(CapabilityName.GET_PROJECT_OVERVIEW);

        assertThatThrownBy(() -> new CapabilityRegistry(List.of(first, duplicate)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(CapabilityName.GET_PROJECT_OVERVIEW.code());
    }

    private MangaAgentCapability capability(CapabilityName name) {
        CapabilityDescriptor descriptor = CapabilityContractSupport.readOnlyDescriptor(
                objectMapper,
                name,
                name.code(),
                "{\"type\":\"object\",\"properties\":{},\"additionalProperties\":false}",
                1
        );
        return new MangaAgentCapability() {
            @Override
            public CapabilityDescriptor descriptor() {
                return descriptor;
            }

            @Override
            public CapabilityResult execute(CapabilityCommand command, AgentRuntimeContext context) {
                return new CapabilityResult(objectMapper.createObjectNode()
                        .put("permission", AgentPermissionPolicy.READ_ONLY.name()));
            }
        };
    }
}
