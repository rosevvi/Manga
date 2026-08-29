package com.manga.service;

import com.manga.common.constant.AiSecretConstants;
import com.manga.common.enums.AiProviderConfigResponseCode;
import com.manga.common.exception.BusinessException;
import com.manga.common.security.SecurityUtils;
import com.manga.dto.AiProviderConfigCreateRequest;
import com.manga.dto.AiProviderConfigResponse;
import com.manga.dto.AiProviderConfigUpdateRequest;
import com.manga.dto.ResolvedAiProviderConfig;
import com.manga.entity.AiProviderConfig;
import com.manga.repository.AiProviderConfigRepository;
import com.manga.service.security.SecretCipher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.util.List;
import java.util.Optional;

/** 管理用户 AI 接入配置，并在服务端加密保存 API Key。 */
@Service
@Slf4j
@RequiredArgsConstructor
public class AiProviderConfigService {

    private final AiProviderConfigRepository configRepository;
    private final SecretCipher secretCipher;

    public List<AiProviderConfigResponse> findAll() {
        long ownerUserId = SecurityUtils.requireCurrentUserId();
        return configRepository.findAllOwnedBy(ownerUserId).stream().map(this::toResponse).toList();
    }

    @Transactional
    public AiProviderConfigResponse create(AiProviderConfigCreateRequest request) {
        long ownerUserId = SecurityUtils.requireCurrentUserId();
        String actor = SecurityUtils.requireCurrentUsername();
        String name = request.name().trim();
        requireUniqueName(ownerUserId, name, null);
        boolean firstConfig = configRepository.findAllOwnedBy(ownerUserId).isEmpty();
        boolean defaultConfig = firstConfig || Boolean.TRUE.equals(request.defaultConfig());
        String apiKey = normalize(request.apiKey());
        AiProviderConfig config = AiProviderConfig.builder()
                .ownerUserId(ownerUserId)
                .name(name)
                .providerType(request.providerType())
                .baseUrl(normalizeBaseUrl(request.baseUrl()))
                .defaultModel(normalize(request.defaultModel()))
                .apiKeyCiphertext(apiKey == null ? null : secretCipher.encrypt(apiKey))
                .apiKeyHint(maskSecret(apiKey))
                .enabled(defaultConfig || request.enabled() == null || request.enabled())
                .defaultConfig(defaultConfig)
                .remark(normalize(request.remark()))
                .createdBy(actor)
                .updatedBy(actor)
                .build();
        if (defaultConfig) {
            configRepository.clearDefault(ownerUserId, actor);
        }
        configRepository.create(config);
        log.info("AI provider config created configId={} ownerUserId={} provider={} defaultConfig={}",
                config.getId(), ownerUserId, config.getProviderType(), config.getDefaultConfig());
        return toResponse(requireOwned(config.getId(), ownerUserId));
    }

    @Transactional
    public AiProviderConfigResponse update(
            long configId, AiProviderConfigUpdateRequest request) {
        long ownerUserId = SecurityUtils.requireCurrentUserId();
        String actor = SecurityUtils.requireCurrentUsername();
        AiProviderConfig current = requireOwned(configId, ownerUserId);
        String name = request.name().trim();
        requireUniqueName(ownerUserId, name, configId);
        boolean removeApiKey = Boolean.TRUE.equals(request.removeApiKey());
        String newApiKey = normalize(request.apiKey());
        boolean keyChanged = removeApiKey || newApiKey != null;
        boolean defaultConfig = request.defaultConfig() == null
                ? Boolean.TRUE.equals(current.getDefaultConfig())
                : request.defaultConfig();
        boolean enabled = request.enabled() == null ? Boolean.TRUE.equals(current.getEnabled()) : request.enabled();
        if (defaultConfig) {
            enabled = true;
            configRepository.clearDefault(ownerUserId, actor);
        }
        AiProviderConfig config = AiProviderConfig.builder()
                .id(configId)
                .name(name)
                .providerType(request.providerType())
                .baseUrl(normalizeBaseUrl(request.baseUrl()))
                .defaultModel(normalize(request.defaultModel()))
                .apiKeyCiphertext(removeApiKey ? null
                        : newApiKey == null ? current.getApiKeyCiphertext() : secretCipher.encrypt(newApiKey))
                .apiKeyHint(removeApiKey ? null
                        : newApiKey == null ? current.getApiKeyHint() : maskSecret(newApiKey))
                .enabled(enabled)
                .defaultConfig(defaultConfig)
                .remark(normalize(request.remark()))
                .updatedBy(actor)
                .build();
        configRepository.update(config, ownerUserId);
        if (Boolean.TRUE.equals(current.getDefaultConfig()) && !defaultConfig) {
            promoteFirstEnabled(ownerUserId, actor, configId);
        }
        log.info("AI provider config updated configId={} ownerUserId={} provider={} enabled={} defaultConfig={} keyChanged={}",
                configId, ownerUserId, config.getProviderType(), enabled, defaultConfig, keyChanged);
        return toResponse(requireOwned(configId, ownerUserId));
    }

    @Transactional
    public void delete(long configId) {
        long ownerUserId = SecurityUtils.requireCurrentUserId();
        String actor = SecurityUtils.requireCurrentUsername();
        AiProviderConfig current = requireOwned(configId, ownerUserId);
        configRepository.delete(configId);
        if (Boolean.TRUE.equals(current.getDefaultConfig())) {
            promoteFirstEnabled(ownerUserId, actor, configId);
        }
        log.info("AI provider config deleted configId={} ownerUserId={}", configId, ownerUserId);
    }

    @Transactional
    public AiProviderConfigResponse setDefault(long configId) {
        long ownerUserId = SecurityUtils.requireCurrentUserId();
        String actor = SecurityUtils.requireCurrentUsername();
        requireOwned(configId, ownerUserId);
        configRepository.clearDefault(ownerUserId, actor);
        configRepository.setDefault(configId, ownerUserId, actor);
        log.info("Default AI provider config changed configId={} ownerUserId={}", configId, ownerUserId);
        return toResponse(requireOwned(configId, ownerUserId));
    }

    /** 为未来 AI 调用解析指定用户当前启用的默认配置。 */
    public Optional<ResolvedAiProviderConfig> resolveDefault(long ownerUserId) {
        return configRepository.findDefaultOwnedBy(ownerUserId).map(config -> new ResolvedAiProviderConfig(
                config.getId(),
                config.getProviderType(),
                config.getBaseUrl(),
                config.getDefaultModel(),
                secretCipher.decrypt(config.getApiKeyCiphertext())));
    }

    private AiProviderConfig requireOwned(long configId, long ownerUserId) {
        return configRepository.findOwnedById(configId, ownerUserId)
                .orElseThrow(() -> new BusinessException(
                        AiProviderConfigResponseCode.CONFIG_NOT_FOUND, HttpStatus.NOT_FOUND));
    }

    private void requireUniqueName(long ownerUserId, String name, Long excludedConfigId) {
        if (configRepository.existsOwnedByName(ownerUserId, name, excludedConfigId)) {
            throw new BusinessException(AiProviderConfigResponseCode.CONFIG_NAME_CONFLICT);
        }
    }

    private void promoteFirstEnabled(long ownerUserId, String actor, long excludedConfigId) {
        configRepository.findAllOwnedBy(ownerUserId).stream()
                .filter(config -> config.getId() != excludedConfigId)
                .filter(config -> Boolean.TRUE.equals(config.getEnabled()))
                .findFirst()
                .ifPresent(config -> configRepository.setDefault(config.getId(), ownerUserId, actor));
    }

    private String normalizeBaseUrl(String baseUrl) {
        String normalized = baseUrl.trim().replaceAll("/+$", "");
        try {
            URI uri = URI.create(normalized);
            boolean supportedScheme = "http".equalsIgnoreCase(uri.getScheme())
                    || "https".equalsIgnoreCase(uri.getScheme());
            if (!supportedScheme || uri.getHost() == null) {
                throw new IllegalArgumentException();
            }
            return normalized;
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(AiProviderConfigResponseCode.BASE_URL_INVALID);
        }
    }

    private AiProviderConfigResponse toResponse(AiProviderConfig config) {
        return new AiProviderConfigResponse(
                config.getId(), config.getName(), config.getProviderType(), config.getBaseUrl(),
                config.getDefaultModel(), config.getApiKeyCiphertext() != null, config.getApiKeyHint(),
                Boolean.TRUE.equals(config.getEnabled()), Boolean.TRUE.equals(config.getDefaultConfig()),
                config.getRemark(), config.getCreatedAt(), config.getUpdatedAt());
    }

    private String maskSecret(String value) {
        if (value == null) {
            return null;
        }
        int visible = AiSecretConstants.API_KEY_HINT_VISIBLE_CHARACTERS;
        if (value.length() <= visible * 2) {
            return AiSecretConstants.API_KEY_HINT_MASK;
        }
        return value.substring(0, visible) + AiSecretConstants.API_KEY_HINT_MASK
                + value.substring(value.length() - visible);
    }

    private String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
