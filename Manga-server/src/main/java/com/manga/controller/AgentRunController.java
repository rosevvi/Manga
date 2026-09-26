package com.manga.controller;

import com.manga.agent.MangaAgentEventStreamService;
import com.manga.agent.MangaAgentRuntimeService;
import com.manga.agent.application.CancelCreativeRunCommand;
import com.manga.agent.application.CreativeRunStartResult;
import com.manga.agent.application.CreativeRuntimeRouter;
import com.manga.agent.application.StartCreativeRunCommand;
import com.manga.common.api.ApiResponse;
import com.manga.common.constant.OpenApiConstants;
import com.manga.common.security.SecurityUtils;
import com.manga.dto.AgentConversationResponse;
import com.manga.dto.AgentEventResponse;
import com.manga.dto.AgentMessageResponse;
import com.manga.dto.AgentRunCreateRequest;
import com.manga.dto.AgentRunResponse;
import jakarta.validation.Valid;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

import java.util.List;

/**
 * 提供当前登录用户使用只读创作助手、查询会话历史及订阅运行事件的 HTTP 接口。
 */
@RestController
@RequestMapping("/api/v1/agent")
@RequiredArgsConstructor
@Tag(name = "创作助手运行", description = "启动、取消和查询创作助手 Run，并订阅会话事件")
@SecurityRequirement(name = OpenApiConstants.BEARER_AUTH_SCHEME)
public class AgentRunController {

    private final CreativeRuntimeRouter runtimeRouter;
    private final MangaAgentRuntimeService legacyQueryService;
    private final MangaAgentEventStreamService eventStreamService;

    /** POST /api/v1/agent/runs：提交一条会话指令，并按灰度规则启动对应创作运行时。 */
    @Operation(summary = "启动创作助手 Run", description = "提交会话指令，并按项目、用户和默认灰度规则选择创作运行时。")
    @PostMapping("/runs")
    public ApiResponse<AgentRunResponse> start(@Valid @RequestBody AgentRunCreateRequest request) {
        long userId = SecurityUtils.requireCurrentUserId();
        CreativeRunStartResult result = runtimeRouter.start(StartCreativeRunCommand.conversation(userId,
                request.conversationId(), request.projectId(), request.message()));
        return ApiResponse.success(toResponse(result));
    }

    /** POST /api/v1/agent/runs/{runId}/cancel：取消当前用户拥有的未结束 Run。 */
    @Operation(summary = "取消创作助手 Run", description = "根据 Run 创建时固化的运行时类型，取消当前用户拥有的未结束 Run。")
    @PostMapping("/runs/{runId}/cancel")
    public ApiResponse<Void> cancel(@PathVariable String runId) {
        runtimeRouter.cancel(new CancelCreativeRunCommand(runId, SecurityUtils.requireCurrentUserId()));
        return ApiResponse.success(null);
    }

    /** GET /api/v1/agent/runs/{runId}：查询当前用户拥有的 Run 状态和终态错误摘要。 */
    @Operation(summary = "查询创作助手 Run", description = "返回当前用户拥有的 Run 状态、执行时间和终态错误摘要。")
    @GetMapping("/runs/{runId}")
    public ApiResponse<AgentRunResponse> findRun(@PathVariable String runId) {
        return ApiResponse.success(legacyQueryService.findRun(runId, SecurityUtils.requireCurrentUserId()));
    }

    /**
     * GET /api/v1/agent/runs/{runId}/events：先从指定序号后回放已提交事件，再通过 SSE 持续推送新事件。
     */
    @Operation(summary = "订阅创作助手 Run 事件", description = "从 afterSequence 之后回放已提交事件，并通过 SSE 持续推送直至 Run 进入终态。")
    @GetMapping(value = "/runs/{runId}/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<AgentEventResponse>> streamEvents(
            @PathVariable String runId,
            @RequestParam(defaultValue = "0") long afterSequence) {
        return eventStreamService.stream(runId, SecurityUtils.requireCurrentUserId(), afterSequence)
                .map(event -> ServerSentEvent.builder(event)
                        .event("agent-event")
                        .id(runId + ':' + event.sequenceNo())
                        .build());
    }

    /** GET /api/v1/agent/conversations：查询当前用户的会话列表，可按项目过滤。 */
    @Operation(summary = "查询创作助手会话", description = "查询当前用户的助手会话列表，并可按项目主键过滤。")
    @GetMapping("/conversations")
    public ApiResponse<List<AgentConversationResponse>> listConversations(
            @RequestParam(required = false) Long projectId) {
        return ApiResponse.success(legacyQueryService.listConversations(
                SecurityUtils.requireCurrentUserId(), projectId));
    }

    /** GET /api/v1/agent/conversations/{conversationId}/messages：查询当前用户拥有的会话消息。 */
    @Operation(summary = "查询创作助手会话消息", description = "按稳定消息顺序返回当前用户拥有的指定会话消息。")
    @GetMapping("/conversations/{conversationId}/messages")
    public ApiResponse<List<AgentMessageResponse>> listMessages(@PathVariable String conversationId) {
        return ApiResponse.success(legacyQueryService.listMessages(conversationId, SecurityUtils.requireCurrentUserId()));
    }

    /** 将运行时门面返回值转换为当前 Legacy API 的响应结构。 */
    private static AgentRunResponse toResponse(CreativeRunStartResult result) {
        return new AgentRunResponse(result.runId(), result.conversationId(), result.projectId(), result.status(),
                result.startedAt(), result.finishedAt(), result.errorCode(), result.errorMessage());
    }
}
