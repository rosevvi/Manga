package com.manga.agent.harness;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.manga.agent.capability.AgentRuntimeContext;
import com.manga.agent.capability.CapabilityCommand;
import com.manga.agent.capability.CapabilityDescriptor;
import com.manga.agent.capability.CapabilityResult;
import com.manga.agent.capability.MangaAgentCapability;
import com.manga.agent.domain.CreativeErrorCode;
import com.manga.agent.domain.CreativeRuntimeMessages;
import io.agentscope.core.message.ToolResultBlock;
import io.agentscope.core.tool.ToolBase;
import io.agentscope.core.tool.ToolCallParam;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.Objects;
import java.time.LocalDateTime;
import com.manga.agent.event.ToolActivitySummaries;

/**
 * 将 Manga 类型化 Capability 安全适配为 AgentScope Tool。
 */
public final class MangaCapabilityTool extends ToolBase {

    private static final TypeReference<Map<String, Object>> INPUT_SCHEMA_TYPE = new TypeReference<>() {
    };

    private final MangaAgentCapability capability;
    private final CapabilityDescriptor descriptor;
    private final ObjectMapper objectMapper;
    private final ToolActivitySummaries summaries;

    public MangaCapabilityTool(MangaAgentCapability capability, ObjectMapper objectMapper, ToolActivitySummaries summaries) {
        super(toolDefinition(capability.descriptor(), objectMapper));
        this.capability = capability;
        this.descriptor = capability.descriptor();
        this.objectMapper = objectMapper;
        this.summaries = summaries;
    }

    /** 从调用上下文解析身份和项目边界后执行能力，并返回结构化 JSON 文本。 */
    @Override
    public Mono<ToolResultBlock> callAsync(ToolCallParam param) {
        return Mono.fromCallable(() -> {
            AgentRuntimeContext context = Objects.isNull(param.getRuntimeContext())
                    ? null
                    : param.getRuntimeContext().get(AgentRuntimeContext.class);
            if (Objects.isNull(context)) {
                throw new IllegalStateException(
                        CreativeRuntimeMessages.CAPABILITY_RUNTIME_CONTEXT_REQUIRED);
            }
            if (!context.permissionPolicy().allows(descriptor.requiredPermission())) {
                throw new AgentRuntimeException(
                        CreativeErrorCode.PERMISSION_DENIED,
                        CreativeRuntimeMessages.CAPABILITY_PERMISSION_DENIED_TEMPLATE
                                .formatted(descriptor.name().code()));
            }
            LocalDateTime started = LocalDateTime.now();
            CapabilityResult result = capability.execute(
                    new CapabilityCommand(objectMapper.valueToTree(param.getInput()),
                            Objects.isNull(param.getToolUseBlock()) ? null : param.getToolUseBlock().getId()),
                    context);
            summaries.record(context, Objects.isNull(param.getToolUseBlock()) ? null : param.getToolUseBlock().getId(),
                    result.summary(), started);
            return ToolResultBlock.text(result.data().toString());
        });
    }

    /** 将 Manifest 使用的 JsonNode Schema 转为 AgentScope ToolBase 定义。 */
    private static ToolBase.Builder toolDefinition(
            CapabilityDescriptor descriptor,
            ObjectMapper objectMapper) {
        Map<String, Object> inputSchema = objectMapper.convertValue(
                descriptor.inputSchema(), INPUT_SCHEMA_TYPE);
        return ToolBase.builder()
                .name(descriptor.name().code())
                .description(descriptor.description())
                .inputSchema(inputSchema)
                .readOnly(descriptor.readOnly())
                .concurrencySafe(descriptor.concurrencySafe());
    }
}
