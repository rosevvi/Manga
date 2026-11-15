package com.manga.agent.harness;

import com.manga.common.exception.BusinessException;
import java.util.List;

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
    private final ToolExecutionSupport support;
    private final InvocationActivity activity;

    public MangaCapabilityTool(MangaAgentCapability capability, ObjectMapper objectMapper, ToolActivitySummaries summaries,
            ToolExecutionSupport support, InvocationActivity activity) {
        super(toolDefinition(capability.descriptor(), objectMapper));
        this.capability = capability;
        this.descriptor = capability.descriptor();
        this.objectMapper = objectMapper;
        this.summaries = summaries;
        this.support = support; this.activity = activity;
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
            String callId = Objects.isNull(param.getToolUseBlock()) ? null : param.getToolUseBlock().getId();
            var input = objectMapper.valueToTree(param.getInput());
            String fingerprint = support.fingerprint(descriptor.name(), input);
            int count = activity.called(fingerprint);
            try {
                String cacheKey = descriptor.readOnly() ? support.cacheKey(descriptor.name(), fingerprint, context) : null;
                CapabilityResult result = Objects.isNull(cacheKey) ? null : activity.cached(cacheKey);
                boolean reused = Objects.nonNull(result);
                if (!reused) {
                    result = capability.execute(new CapabilityCommand(input, callId), context);
                    if (Objects.nonNull(cacheKey)) { activity.remember(cacheKey, result); }
                }
                activity.succeeded(Objects.isNull(cacheKey) ? fingerprint : cacheKey, descriptor.readOnly());
                summaries.record(context, callId, result.summary(), started, fingerprint, reused, count, null);
                return ToolResultBlock.text(result.data().toString());
            } catch (RuntimeException failure) {
                String reason = support.safeFailure(failure);
                summaries.record(context, callId, null, started, fingerprint, false, count, reason);
                if (failure instanceof ToolInputValidationException invalid) {
                    return ToolResultBlock.error(objectMapper.valueToTree(new InvalidInputResult(
                            ToolInputValidationException.CODE, reason, invalid.issues())).toString());
                }
                if (failure instanceof BusinessException
                        || failure instanceof AgentRuntimeException agentFailure
                        && agentFailure.errorCode() != CreativeErrorCode.OWNER_LEASE_LOST) {
                    return ToolResultBlock.error(reason);
                }
                activity.abort(failure instanceof AgentRuntimeException agentFailure ? agentFailure
                        : new AgentRuntimeException(CreativeErrorCode.CAPABILITY_EXECUTION_FAILED, reason, failure));
                return ToolResultBlock.error(reason);
            }
        });
    }

    /** 模型可安全消费的字段错误，不包含被拒绝的原始值。 */
    private record InvalidInputResult(/** 稳定错误类型。 */ String code, /** 公开原因。 */ String message,
            /** 可修正的字段约束。 */ List<ToolInputValidationException.FieldIssue> fieldErrors) { }

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
