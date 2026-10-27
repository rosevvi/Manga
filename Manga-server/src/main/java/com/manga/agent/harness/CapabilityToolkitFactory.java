package com.manga.agent.harness;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.manga.agent.blueprint.AgentBlueprint;
import com.manga.agent.blueprint.CapabilityManifest;
import com.manga.agent.capability.CapabilityName;
import com.manga.agent.capability.CapabilityRegistry;
import com.manga.agent.domain.CreativeErrorCode;
import com.manga.agent.domain.CreativeRuntimeMessages;
import io.agentscope.core.tool.Toolkit;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.stream.Collectors;

/**
 * 按 Blueprint 精确白名单创建不含额外工具的 AgentScope Toolkit。
 */
@Component
@RequiredArgsConstructor
public class CapabilityToolkitFactory {

    private final CapabilityRegistry capabilityRegistry;
    private final ObjectMapper objectMapper;

    /** 注册 Blueprint 明确列出的全部能力，并复核最终工具集合完全相等。 */
    public Toolkit create(AgentBlueprint blueprint) {
        Toolkit toolkit = new Toolkit();
        for (CapabilityManifest manifest : blueprint.capabilities()) {
            toolkit.registerAgentTool(new MangaCapabilityTool(
                    capabilityRegistry.require(manifest.name()), objectMapper));
        }
        Set<String> expected = blueprint.capabilities().stream()
                .map(CapabilityManifest::name)
                .map(CapabilityName::code)
                .collect(Collectors.toUnmodifiableSet());
        if (!toolkit.getToolNames().equals(expected)) {
            throw new AgentRuntimeException(
                    CreativeErrorCode.RUN_CONFIG_UNAVAILABLE,
                    CreativeRuntimeMessages.CAPABILITY_TOOLKIT_MISMATCH);
        }
        return toolkit;
    }
}
