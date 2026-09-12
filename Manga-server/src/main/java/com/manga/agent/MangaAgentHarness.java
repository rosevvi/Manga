package com.manga.agent;

import com.manga.config.properties.MangaAgentProperties;
import com.manga.dto.ResolvedAiProviderConfig;
import com.manga.service.ProjectService;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.event.AgentEvent;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.UserMessage;
import io.agentscope.core.state.AgentStateStore;
import io.agentscope.core.tool.Toolkit;
import io.agentscope.core.tool.ToolkitConfig;
import io.agentscope.core.permission.PermissionContextState;
import io.agentscope.core.permission.PermissionMode;
import io.agentscope.harness.agent.HarnessAgent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;

import java.util.List;

/** 创建一次性、受限的 Manga AgentScope Harness。 */
@Component
@RequiredArgsConstructor
public class MangaAgentHarness {

    private final MangaAgentModelFactory modelFactory;
    private final MangaProjectContextService projectContextService;
    private final ProjectService projectService;
    private final AgentStateStore agentStateStore;
    private final MangaAgentProperties properties;
    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper;

    public Flux<AgentEvent> stream(MangaKernelSpec spec, ResolvedAiProviderConfig providerConfig,
            MangaAgentContext executionContext, String stateSessionId, String message) {
        if (executionContext.projectId() != null) {
            projectService.requireAccessibleProject(executionContext.projectId(), executionContext.userId());
        }
        Toolkit toolkit = new Toolkit(ToolkitConfig.builder().parallel(false).build());
        toolkit.registerAgentTool(new MangaProjectContextTool(projectContextService, objectMapper, Schedulers.boundedElastic()));
        HarnessAgent agent = HarnessAgent.builder()
                .agentId(spec.agentKey())
                .name("Manga 创作助手")
                .description("负责分析项目、剧本和分镜的受限创作助手")
                .sysPrompt(spec.systemPrompt())
                .model(modelFactory.create(providerConfig, spec.modelCode()))
                .stateStore(agentStateStore)
                .toolkit(toolkit)
                .permissionContext(PermissionContextState.builder().mode(PermissionMode.BYPASS).build())
                .maxIters(properties.getRuntime().getMaxIterations())
                .disableFilesystemTools()
                .disableShellTool()
                .disableMemoryTools()
                .disableMemoryHooks()
                .disableSessionPersistence()
                .disableWorkspaceContext()
                .disableAtPathExpansion()
                .disableSubagents()
                .disableDynamicSubagents()
                .disableToolsConfig()
                .build();
        RuntimeContext runtimeContext = RuntimeContext.builder()
                .userId(String.valueOf(executionContext.userId()))
                .sessionId(stateSessionId)
                .put(MangaAgentContext.class, executionContext)
                .build();
        List<Msg> messages = List.of(new UserMessage(message));
        return agent.streamEvents(messages, runtimeContext).doFinally(ignored -> agent.close());
    }
}
