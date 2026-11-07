package com.manga.controller;

import com.manga.agent.generation.GenerationCallbackService;
import com.manga.agent.generation.GenerationConstants;
import com.manga.agent.generation.GenerationError;
import com.manga.agent.generation.GenerationProviderKey;
import com.manga.common.api.ApiResponse;
import com.manga.common.exception.BusinessException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.io.IOException;

/** 平台中继签名回调入口，独立 HMAC 认证，不依赖浏览器登录。 */
@RestController
@RequestMapping(GenerationConstants.CALLBACK_API)
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Generation Callback", description = "平台异步中继回调；不兼容 OpenAI 原生 Webhook")
public class GenerationCallbackController {
    private final GenerationCallbackService callbacks;
    /** 有界读取原始字节；签名验证后才持久化，重复事件幂等。 */
    @PostMapping("/{provider}")
    @Operation(summary = "接收平台异步作业中继提示", description = "HMAC-SHA256(timestamp + 换行 + 原始正文)；目前仅开放 Fake 验证。回调不直接应用媒体。")
    public ApiResponse<Boolean> receive(@PathVariable GenerationProviderKey provider,
            @RequestHeader(GenerationConstants.CALLBACK_TIMESTAMP_HEADER) long timestamp,
            @RequestHeader(GenerationConstants.CALLBACK_SIGNATURE_HEADER) String signature, HttpServletRequest request) throws IOException {
        byte[] body;
        try (var input = request.getInputStream()) { body = input.readNBytes(GenerationConstants.CALLBACK_MAX_BYTES + 1); }
        if (body.length > GenerationConstants.CALLBACK_MAX_BYTES) throw new BusinessException(GenerationError.CALLBACK, HttpStatus.BAD_REQUEST);
        callbacks.receive(provider, timestamp, signature, body);
        log.debug("Generation callback accepted provider={} bytes={}", provider, body.length);
        return ApiResponse.success(true);
    }
}
