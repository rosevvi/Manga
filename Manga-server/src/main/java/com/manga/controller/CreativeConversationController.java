package com.manga.controller;

import com.manga.agent.api.CreativeApiConstants;
import com.manga.agent.api.CreativeConversationHistory;
import com.manga.agent.api.CreativeConversationView;
import com.manga.agent.application.CreativeConversationService;
import com.manga.common.api.ApiResponse;
import com.manga.common.constant.OpenApiConstants;
import com.manga.common.security.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import java.util.List;

/** 为助手提供会话列表、服务端消息历史和投影重建接口。 */
@RestController
@RequestMapping(CreativeApiConstants.CREATIVE_CONVERSATIONS_PATH)
@RequiredArgsConstructor
@Validated
@Slf4j
@Tag(name = "Creative Conversation", description = "连续对话、消息历史与事件投影")
@SecurityRequirement(name = OpenApiConstants.BEARER_AUTH_SCHEME)
public class CreativeConversationController {
    private final CreativeConversationService service;

    /** 分页列出当前认证用户的会话。 */
    @Operation(summary = "查询创作会话列表", description = "支持项目过滤及 beforeId 分页；返回服务端会话摘要。")
    @GetMapping
    public ApiResponse<List<CreativeConversationView>> list(
            @RequestParam(required = false) @Positive Long projectId,
            @RequestParam(required = false) @Positive Long beforeId) {
        long userId = SecurityUtils.requireCurrentUserId();
        List<CreativeConversationView> result = service.list(userId, projectId, beforeId);
        log.debug("Conversation list userId={} projectId={} count={}", userId, projectId, result.size());
        return ApiResponse.success(result);
    }

    /** 读取消息投影和与之匹配的活动 Run cursor。 */
    @Operation(summary = "查询创作消息历史", description = "返回完整消息、工具状态、历史分页及 SSE 恢复水位。")
    @GetMapping("/{conversationId}/messages")
    public ApiResponse<CreativeConversationHistory> history(
            @PathVariable String conversationId, @RequestParam(required = false) @Positive Long beforeId) {
        long userId = SecurityUtils.requireCurrentUserId();
        CreativeConversationHistory result = service.history(userId, conversationId, beforeId);
        log.debug("Conversation history userId={} conversationId={} count={}", userId, conversationId, result.messages().size());
        return ApiResponse.success(result);
    }

    /** 从事实事件重新生成消息；活动会话拒绝重建。 */
    @Operation(summary = "重建创作消息投影", description = "按事件重建当前用户会话全部消息；存在活动 Run 时返回冲突。")
    @PostMapping("/{conversationId}/messages/rebuild")
    public ApiResponse<Void> rebuild(@PathVariable String conversationId) {
        long userId = SecurityUtils.requireCurrentUserId();
        service.rebuild(userId, conversationId);
        log.info("Conversation messages rebuilt userId={} conversationId={}", userId, conversationId);
        return ApiResponse.success(null);
    }
}
