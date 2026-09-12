package com.manga.controller;

import com.manga.agent.MangaAgentEventStreamService;
import com.manga.agent.MangaAgentRuntimeService;
import com.manga.common.api.ApiResponse;
import com.manga.common.security.SecurityUtils;
import com.manga.dto.AgentConversationResponse;
import com.manga.dto.AgentEventResponse;
import com.manga.dto.AgentMessageResponse;
import com.manga.dto.AgentRunCreateRequest;
import com.manga.dto.AgentRunResponse;
import jakarta.validation.Valid;
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

/** 提供首期只读创作助手的运行、历史和 SSE 接口。 */
@RestController
@RequestMapping("/api/v1/agent")
@RequiredArgsConstructor
public class AgentRunController {

    private final MangaAgentRuntimeService runtimeService;
    private final MangaAgentEventStreamService eventStreamService;

    @PostMapping("/runs")
    public ApiResponse<AgentRunResponse> start(@Valid @RequestBody AgentRunCreateRequest request) {
        return ApiResponse.success(runtimeService.start(SecurityUtils.requireCurrentUserId(), request));
    }

    @PostMapping("/runs/{runId}/cancel")
    public ApiResponse<Void> cancel(@PathVariable String runId) {
        runtimeService.cancel(runId, SecurityUtils.requireCurrentUserId());
        return ApiResponse.success(null);
    }

    @GetMapping("/runs/{runId}")
    public ApiResponse<AgentRunResponse> run(@PathVariable String runId) {
        return ApiResponse.success(runtimeService.findRun(runId, SecurityUtils.requireCurrentUserId()));
    }

    @GetMapping(value = "/runs/{runId}/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<AgentEventResponse>> events(
            @PathVariable String runId,
            @RequestParam(defaultValue = "0") long afterSequence) {
        return eventStreamService.stream(runId, SecurityUtils.requireCurrentUserId(), afterSequence)
                .map(event -> ServerSentEvent.builder(event)
                        .event("agent-event")
                        .id(runId + ':' + event.sequenceNo())
                        .build());
    }

    @GetMapping("/conversations")
    public ApiResponse<List<AgentConversationResponse>> conversations(@RequestParam(required = false) Long projectId) {
        return ApiResponse.success(runtimeService.listConversations(SecurityUtils.requireCurrentUserId(), projectId));
    }

    @GetMapping("/conversations/{conversationId}/messages")
    public ApiResponse<List<AgentMessageResponse>> messages(@PathVariable String conversationId) {
        return ApiResponse.success(runtimeService.listMessages(conversationId, SecurityUtils.requireCurrentUserId()));
    }
}
