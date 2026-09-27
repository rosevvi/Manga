package com.manga.controller;

import com.manga.agent.api.CreateCreativeRunRequest;
import com.manga.agent.api.CreativeApiConstants;
import com.manga.agent.api.CreativeEventCursor;
import com.manga.agent.api.CreativeRunResponse;
import com.manga.agent.application.CancelCreativeRunCommand;
import com.manga.agent.application.CreativeEventStreamService;
import com.manga.agent.application.CreativeRunQueryService;
import com.manga.agent.application.CreativeRunStartResult;
import com.manga.agent.application.CreativeRuntime;
import com.manga.agent.application.StartCreativeRunCommand;
import com.manga.agent.event.CreativeEventEnvelope;
import com.manga.common.api.ApiResponse;
import com.manga.common.constant.OpenApiConstants;
import com.manga.common.security.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

import java.util.Objects;

/**
 * 提供会话和页面手动入口共用的 Creative Run HTTP/SSE 契约。
 */
@RestController
@RequestMapping(CreativeApiConstants.CREATIVE_RUNS_PATH)
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Creative Run", description = "启动、取消、查询创作 Run，并按游标订阅版本化事件")
@SecurityRequirement(name = OpenApiConstants.BEARER_AUTH_SCHEME)
public class CreativeRunController {

    private final CreativeRuntime creativeRuntime;
    private final CreativeRunQueryService runQueryService;
    private final CreativeEventStreamService eventStreamService;

    /** 创建会话或手动触发的 Creative Run，并返回已持久化状态快照。 */
    @Operation(summary = "创建 Creative Run", description = "支持助手会话和页面手动操作，requestId 用于启动幂等。")
    @PostMapping
    public ResponseEntity<ApiResponse<CreativeRunResponse>> start(
            @Valid @RequestBody CreateCreativeRunRequest request) {
        long userId = SecurityUtils.requireCurrentUserId();
        log.info("Creative Run requested userId={} requestId={} projectId={} triggerSource={} operationType={}",
                userId, request.requestId(), request.projectId(), request.triggerSource(), request.operationType());
        CreativeRunStartResult started = creativeRuntime.start(toCommand(userId, request));
        CreativeRunResponse response = runQueryService.findOwned(started.runId(), userId);
        log.info("Creative Run accepted userId={} runId={} runtimeStatus={}",
                userId, response.runId(), response.status());
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(ApiResponse.success(response));
    }

    /** 查询当前用户拥有的 Creative Run 状态快照。 */
    @Operation(summary = "查询 Creative Run", description = "返回 Run 固化的操作、权限、版本和当前状态。")
    @GetMapping("/{runId}")
    public ApiResponse<CreativeRunResponse> find(@PathVariable String runId) {
        long userId = SecurityUtils.requireCurrentUserId();
        return ApiResponse.success(runQueryService.findOwned(runId, userId));
    }

    /** 请求取消当前用户拥有的 Creative Run。 */
    @Operation(summary = "取消 Creative Run", description = "幂等取消当前用户拥有的未结束 Run。")
    @PostMapping("/{runId}/cancel")
    public ApiResponse<Void> cancel(@PathVariable String runId) {
        long userId = SecurityUtils.requireCurrentUserId();
        log.info("Creative Run cancellation requested userId={} runId={}", userId, runId);
        creativeRuntime.cancel(new CancelCreativeRunCommand(runId, userId));
        return ApiResponse.success(null);
    }

    /** 从查询参数或 Last-Event-ID 指定的游标后回放事件，再通过 SSE 持续推送。 */
    @Operation(summary = "订阅 Creative Run 事件", description = "支持 afterSequence 和 Last-Event-ID 断点恢复。")
    @GetMapping(value = "/{runId}/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<CreativeEventEnvelope>> streamEvents(
            @PathVariable String runId,
            @RequestParam(required = false) Long afterSequence,
            @RequestHeader(name = CreativeApiConstants.LAST_EVENT_ID_HEADER, required = false) String lastEventId) {
        long userId = SecurityUtils.requireCurrentUserId();
        long cursor = CreativeEventCursor.resolve(runId, afterSequence, lastEventId);
        log.info("Creative Run event stream opened userId={} runId={} afterSequence={}", userId, runId, cursor);
        return eventStreamService.stream(runId, userId, cursor)
                .map(event -> ServerSentEvent.builder(event)
                        .event(CreativeApiConstants.CREATIVE_EVENT_NAME)
                        .id(runId + CreativeApiConstants.EVENT_ID_SEPARATOR + event.sequence())
                        .build());
    }

    /** 将经过 Bean Validation 的 API 请求转换为应用层启动命令。 */
    private StartCreativeRunCommand toCommand(long userId, CreateCreativeRunRequest request) {
        return new StartCreativeRunCommand(
                userId,
                request.requestId(),
                request.triggerSource(),
                request.operationType(),
                request.conversationId(),
                request.projectId(),
                Objects.isNull(request.target()) ? null : request.target().toDomain(),
                request.permissionPolicy(),
                request.instruction()
        );
    }

}
