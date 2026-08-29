package com.manga.controller;

import com.manga.common.api.ApiResponse;
import com.manga.common.security.SecurityUtils;
import com.manga.dto.AiProviderConfigCreateRequest;
import com.manga.dto.AiProviderConfigResponse;
import com.manga.dto.AiProviderConfigUpdateRequest;
import com.manga.service.AiProviderConfigService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 提供当前用户 AI 服务配置的安全管理接口。 */
@RestController
@RequestMapping("/api/v1/ai-provider-configs")
@Slf4j
@RequiredArgsConstructor
public class AiProviderConfigController {

    private final AiProviderConfigService configService;

    @GetMapping
    public ApiResponse<List<AiProviderConfigResponse>> findAll() {
        log.info("[AiProviderConfigController#findAll] request subject={}", SecurityUtils.getCurrentUsername());
        List<AiProviderConfigResponse> result = configService.findAll();
        log.info("[AiProviderConfigController#findAll] response count={}", result.size());
        return ApiResponse.success(result);
    }

    @PostMapping
    public ApiResponse<AiProviderConfigResponse> create(
            @Valid @RequestBody AiProviderConfigCreateRequest request) {
        log.info("[AiProviderConfigController#create] request subject={} name={} provider={} keyPresent={}",
                SecurityUtils.getCurrentUsername(), request.name(), request.providerType(), hasText(request.apiKey()));
        AiProviderConfigResponse result = configService.create(request);
        log.info("[AiProviderConfigController#create] response configId={} provider={} defaultConfig={}",
                result.id(), result.providerType(), result.defaultConfig());
        return ApiResponse.success(result);
    }

    @PutMapping("/{configId}")
    public ApiResponse<AiProviderConfigResponse> update(
            @PathVariable long configId,
            @Valid @RequestBody AiProviderConfigUpdateRequest request) {
        boolean keyChanged = hasText(request.apiKey()) || Boolean.TRUE.equals(request.removeApiKey());
        log.info("[AiProviderConfigController#update] request configId={} subject={} provider={} keyChanged={}",
                configId, SecurityUtils.getCurrentUsername(), request.providerType(), keyChanged);
        AiProviderConfigResponse result = configService.update(configId, request);
        log.info("[AiProviderConfigController#update] response configId={} enabled={} defaultConfig={}",
                result.id(), result.enabled(), result.defaultConfig());
        return ApiResponse.success(result);
    }

    @DeleteMapping("/{configId}")
    public ApiResponse<Void> delete(@PathVariable long configId) {
        log.info("[AiProviderConfigController#delete] request configId={} subject={}",
                configId, SecurityUtils.getCurrentUsername());
        configService.delete(configId);
        log.info("[AiProviderConfigController#delete] response configId={} success={}", configId, true);
        return ApiResponse.success(null);
    }

    @PutMapping("/{configId}/default")
    public ApiResponse<AiProviderConfigResponse> setDefault(@PathVariable long configId) {
        log.info("[AiProviderConfigController#setDefault] request configId={} subject={}",
                configId, SecurityUtils.getCurrentUsername());
        AiProviderConfigResponse result = configService.setDefault(configId);
        log.info("[AiProviderConfigController#setDefault] response configId={} defaultConfig={}",
                result.id(), result.defaultConfig());
        return ApiResponse.success(result);
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
