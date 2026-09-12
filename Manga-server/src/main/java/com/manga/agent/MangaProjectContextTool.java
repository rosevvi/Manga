package com.manga.agent;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.message.ToolResultBlock;
import io.agentscope.core.message.ToolUseBlock;
import io.agentscope.core.tool.ToolBase;
import io.agentscope.core.tool.ToolCallParam;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;

import java.util.Map;
import java.util.Objects;

/** 仅向 Agent 提供当前授权项目的概要、章节摘要和分镜资料。 */
public final class MangaProjectContextTool extends ToolBase {

    public static final String NAME = "get_project_context";

    private final MangaProjectContextService contextService;
    private final ObjectMapper objectMapper;
    private final Scheduler scheduler;

    public MangaProjectContextTool(MangaProjectContextService contextService, ObjectMapper objectMapper,
            Scheduler scheduler) {
        super(toolBuilder());
        this.contextService = Objects.requireNonNull(contextService, "contextService must not be null");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler must not be null");
    }

    @Override
    public Mono<ToolResultBlock> callAsync(ToolCallParam param) {
        return Mono.fromCallable(() -> execute(param)).subscribeOn(scheduler);
    }

    private ToolResultBlock execute(ToolCallParam param) throws JsonProcessingException {
        if (param == null || param.getRuntimeContext() == null || param.getToolUseBlock() == null) {
            throw new IllegalArgumentException("助手工具调用缺少运行上下文");
        }
        RuntimeContext runtimeContext = param.getRuntimeContext();
        MangaAgentContext context = runtimeContext.get(MangaAgentContext.class);
        if (context == null) {
            throw new IllegalStateException("助手工具调用缺少授权上下文");
        }
        long projectId = resolveProjectId(param.getInput(), context.projectId());
        String result = objectMapper.writeValueAsString(contextService.getProjectContext(context.userId(), projectId));
        ToolUseBlock toolUse = param.getToolUseBlock();
        return ToolResultBlock.text(result).withIdAndName(toolUse.getId(), toolUse.getName());
    }

    private long resolveProjectId(Map<String, Object> input, Long boundProjectId) {
        Object raw = input == null ? null : input.get("projectId");
        if (raw == null && boundProjectId != null) {
            return boundProjectId;
        }
        if (raw instanceof Number number) {
            return number.longValue();
        }
        if (raw instanceof String text && !text.isBlank()) {
            return Long.parseLong(text);
        }
        throw new IllegalArgumentException("请先在助手中绑定一个项目");
    }

    private static ToolBase.Builder toolBuilder() {
        Map<String, Object> schema = Map.of(
                "type", "object",
                "properties", Map.of("projectId", Map.of("type", "integer", "description", "需要读取的项目 ID；已绑定项目时可省略")),
                "additionalProperties", false);
        return ToolBase.builder().name(NAME).description("读取授权项目的概要、剧本章节摘要和分镜镜头，不能修改任何数据")
                .inputSchema(schema).readOnly(true).concurrencySafe(true);
    }
}
