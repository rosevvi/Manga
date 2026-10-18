package com.manga.agent.harness;

import com.manga.agent.blueprint.AgentBlueprint;
import com.manga.agent.blueprint.AgentBlueprintPreflight;
import com.manga.agent.model.AgentModelFactory;
import com.manga.agent.model.OwnedChatModel;
import io.agentscope.core.state.AgentStateStore;
import io.agentscope.core.tool.Toolkit;
import io.agentscope.harness.agent.HarnessAgent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Objects;

/**
 * 按已预检 Blueprint 创建只含只读白名单能力的受控 Kernel。
 */
@Component
@RequiredArgsConstructor
public class MangaHarnessFactory {

    /** AgentScope 中只用于诊断的稳定 Director 描述。 */
    private static final String DIRECTOR_DESCRIPTION = "Manga read-only creative director";

    private final AgentBlueprintPreflight blueprintPreflight;
    private final AgentModelFactory modelFactory;
    private final CapabilityToolkitFactory toolkitFactory;
    private final AgentStateStore stateStore;

    /**
     * 先完成配置契约预检，再创建模型和 Toolkit；中途失败时立即关闭已创建资源。
     */
    public AgentKernel createKernel(AgentBlueprint blueprint) {
        blueprintPreflight.validate(blueprint);
        OwnedChatModel model = modelFactory.create(blueprint.model());
        Toolkit toolkit = null;
        try {
            toolkit = toolkitFactory.create(blueprint);
            Toolkit stableToolkit = toolkit;
            HarnessProvider provider = context -> new AgentScopeHarnessLease(
                    buildHarness(blueprint, model, stableToolkit.copy()), context);
            return new AgentKernel(blueprint, model, stableToolkit, provider);
        } catch (RuntimeException | Error exception) {
            if (Objects.nonNull(toolkit)) {
                toolkit.closeMcpClients();
            }
            model.close();
            throw exception;
        }
    }

    /** 创建禁用文件、Shell、Web、动态 Skill 和 SubAgent 的隔离 Harness。 */
    private HarnessAgent buildHarness(
            AgentBlueprint blueprint,
            OwnedChatModel model,
            Toolkit toolkit) {
        return HarnessAgent.builder()
                .agentId(blueprint.kernelKey().value())
                .name(blueprint.profileKey().code())
                .description(DIRECTOR_DESCRIPTION)
                .sysPrompt(blueprint.systemPrompt())
                .model(model.model())
                .toolkit(toolkit)
                .stateStore(stateStore)
                .maxIters(blueprint.maxTurns())
                .disableFilesystemTools()
                .disableShellTool()
                .disableWebTools()
                .disableMemoryTools()
                .disableMemoryHooks()
                .disableTranscript()
                .disableWorkspaceContext()
                .disableAtPathExpansion()
                .disableSubagents()
                .disableDynamicSubagents()
                .disableDynamicSkills()
                .disableDefaultWorkspaceSkills()
                .disableToolsConfig()
                .build();
    }
}
