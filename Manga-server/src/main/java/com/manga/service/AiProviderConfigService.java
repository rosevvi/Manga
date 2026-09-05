package com.manga.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.manga.common.constant.AiSecretConstants;
import com.manga.common.constant.AiProviderConstants;
import com.manga.common.enums.AiProviderConfigResponseCode;
import com.manga.common.enums.AiProviderType;
import com.manga.common.enums.AiProxyType;
import com.manga.common.exception.BusinessException;
import com.manga.common.security.SecurityUtils;
import com.manga.dto.AiProviderConnectionTestRequest;
import com.manga.dto.AiProviderConnectionTestResponse;
import com.manga.dto.AiProviderConfigCreateRequest;
import com.manga.dto.AiProviderConfigResponse;
import com.manga.dto.AiProviderConfigUpdateRequest;
import com.manga.dto.AiProviderOptionResponse;
import com.manga.dto.ResolvedAiProviderConfig;
import com.manga.entity.AiProviderConfig;
import com.manga.integration.ai.AiProviderHttpClient;
import com.manga.integration.ai.AiProviderProxySettings;
import com.manga.repository.AiProviderConfigRepository;
import com.manga.service.security.SecretCipher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

/** 管理用户 AI 接入配置，并在服务端加密保存 API Key。 */
@Service
@Slf4j
@RequiredArgsConstructor
public class AiProviderConfigService {

    /** 用于移除上游响应中脚本和样式块的规则。 */
    private static final Pattern SCRIPT_BLOCK_PATTERN = Pattern.compile(
            "(?is)<(script|style)\\b[^>]*>.*?</\\1>");
    /** 用于移除上游响应中 HTML 标签的规则。 */
    private static final Pattern HTML_TAG_PATTERN = Pattern.compile("(?s)<[^>]+>");
    /** 用于合并响应摘要连续空白的规则。 */
    private static final Pattern WHITESPACE_PATTERN = Pattern.compile("\\s+");
    /** 用于识别并隐藏 Bearer Token 的规则。 */
    private static final Pattern BEARER_TOKEN_PATTERN = Pattern.compile("(?i)bearer\\s+[A-Za-z0-9._~+/=-]+");
    /** 用于识别并隐藏查询参数密钥的规则。 */
    private static final Pattern SENSITIVE_QUERY_PATTERN = Pattern.compile(
            "(?i)([?&](api[_-]?key|key|token)=)([^\\s&<>\"']+)");
    /** 用于识别并隐藏响应字段密钥的规则。 */
    private static final Pattern SENSITIVE_FIELD_PATTERN = Pattern.compile(
            "(?i)(api[_ -]?key|authorization|token|secret)([\"'\\s:=]+)([^\"'\\s,;<>]{8,})");

    private final AiProviderConfigRepository configRepository;
    private final SecretCipher secretCipher;
    private final ObjectMapper objectMapper;
    private final AiProviderHttpClient aiProviderHttpClient;

    /** 查询可供前端展示的 AI 服务商元数据。 */
    public List<AiProviderOptionResponse> findProviderOptions() {
        return Arrays.stream(AiProviderType.values())
                .map(provider -> new AiProviderOptionResponse(
                        provider, provider.label(), provider.defaultBaseUrl(), provider.recommendedModel(),
                        provider.apiKeyRequired(), provider.capabilities(), provider.description()))
                .toList();
    }

    /** 查询当前用户的 AI 服务配置。 */
    public List<AiProviderConfigResponse> findAll() {
        long ownerUserId = SecurityUtils.requireCurrentUserId();
        return configRepository.findAllOwnedBy(ownerUserId).stream().map(this::toResponse).toList();
    }

    /** 创建并加密保存 AI 服务配置。 */
    @Transactional
    public AiProviderConfigResponse create(AiProviderConfigCreateRequest request) {
        long ownerUserId = SecurityUtils.requireCurrentUserId();
        String actor = SecurityUtils.requireCurrentUsername();
        String name = request.name().trim();
        requireUniqueName(ownerUserId, name, null);
        boolean firstConfig = configRepository.findAllOwnedBy(ownerUserId).isEmpty();
        boolean defaultConfig = firstConfig || Boolean.TRUE.equals(request.defaultConfig());
        String apiKey = normalize(request.apiKey());
        ProxySaveFields proxyFields = proxySaveFields(
                request.proxyType(), request.proxyHost(), request.proxyPort(), request.proxyUsername(),
                request.proxyPassword(), false, null, null);
        AiProviderConfig config = AiProviderConfig.builder()
                .ownerUserId(ownerUserId)
                .name(name)
                .providerType(request.providerType())
                .baseUrl(normalizeBaseUrl(request.baseUrl()))
                .defaultModel(normalize(request.defaultModel()))
                .apiKeyCiphertext(apiKey == null ? null : secretCipher.encrypt(apiKey))
                .apiKeyHint(maskSecret(apiKey))
                .proxyType(proxyFields.proxyType())
                .proxyHost(proxyFields.proxyHost())
                .proxyPort(proxyFields.proxyPort())
                .proxyUsername(proxyFields.proxyUsername())
                .proxyPasswordCiphertext(proxyFields.proxyPasswordCiphertext())
                .proxyPasswordHint(proxyFields.proxyPasswordHint())
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

    /** 更新并安全处理 AI 服务配置。 */
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
        ProxySaveFields proxyFields = proxySaveFields(
                request.proxyType(), request.proxyHost(), request.proxyPort(), request.proxyUsername(),
                request.proxyPassword(), Boolean.TRUE.equals(request.removeProxyPassword()),
                current.getProxyPasswordCiphertext(), current.getProxyPasswordHint());
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
                .proxyType(proxyFields.proxyType())
                .proxyHost(proxyFields.proxyHost())
                .proxyPort(proxyFields.proxyPort())
                .proxyUsername(proxyFields.proxyUsername())
                .proxyPasswordCiphertext(proxyFields.proxyPasswordCiphertext())
                .proxyPasswordHint(proxyFields.proxyPasswordHint())
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

    /** 删除 AI 服务配置并补选默认项。 */
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

    /** 设置当前用户的默认 AI 服务配置。 */
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
                secretCipher.decrypt(config.getApiKeyCiphertext()),
                AiProxyType.normalize(config.getProxyType()),
                config.getProxyHost(),
                config.getProxyPort(),
                config.getProxyUsername(),
                config.getProxyPasswordCiphertext() == null
                        ? null
                        : secretCipher.decrypt(config.getProxyPasswordCiphertext())));
    }

    /** 检测保存的服务地址和认证是否可用，返回不包含密钥的结果摘要。 */
    public AiProviderConnectionTestResponse testConnection(long configId) {
        long ownerUserId = SecurityUtils.requireCurrentUserId();
        AiProviderConfig config = requireOwned(configId, ownerUserId);
        String apiKey = config.getApiKeyCiphertext() == null ? null : secretCipher.decrypt(config.getApiKeyCiphertext());
        String proxyPassword = config.getProxyPasswordCiphertext() == null
                ? null
                : secretCipher.decrypt(config.getProxyPasswordCiphertext());
        return testConnection(config, apiKey, proxyPassword);
    }

    /** 检测编辑中的服务地址和密钥，未保存的密钥不会写入数据库。 */
    public AiProviderConnectionTestResponse testConnection(AiProviderConnectionTestRequest request) {
        long ownerUserId = SecurityUtils.requireCurrentUserId();
        AiProviderConfig savedConfig = request.configId() == null ? null : requireOwned(request.configId(), ownerUserId);
        String apiKey = normalize(request.apiKey());
        if (apiKey == null && savedConfig != null && !Boolean.TRUE.equals(request.removeApiKey())) {
            apiKey = savedConfig.getApiKeyCiphertext() == null
                    ? null
                    : secretCipher.decrypt(savedConfig.getApiKeyCiphertext());
        }
        String proxyPassword = resolveDraftProxyPassword(request, savedConfig);
        AiProviderConfig config = AiProviderConfig.builder()
                .id(request.configId())
                .providerType(request.providerType())
                .baseUrl(normalizeBaseUrl(request.baseUrl()))
                .defaultModel(normalize(request.defaultModel()))
                .proxyType(AiProxyType.normalize(request.proxyType()))
                .proxyHost(normalize(request.proxyHost()))
                .proxyPort(request.proxyPort())
                .proxyUsername(normalize(request.proxyUsername()))
                .build();
        return testConnection(config, apiKey, proxyPassword);
    }

    /** 检测 AI 服务连接并生成安全结果摘要。 */
    private AiProviderConnectionTestResponse testConnection(AiProviderConfig config, String apiKey, String proxyPassword) {
        Instant startedAt = Instant.now();
        if (config.getProviderType().apiKeyRequired() && (apiKey == null || apiKey.isBlank())) {
            return connectionResult(false, null, AiProviderConstants.CONNECTION_TEST_API_KEY_REQUIRED, startedAt);
        }
        try {
            ResponseEntity<String> response = aiProviderHttpClient.probe(
                    config.getProviderType(),
                    normalizeBaseUrl(config.getBaseUrl()),
                    apiKey,
                    proxyRuntimeSettings(config, proxyPassword));
            int statusCode = response.getStatusCode().value();
            boolean reachable = statusCode >= 200 && statusCode < 300;
            String message = httpStatusMessage(statusCode);
            if (reachable && shouldCheckModel(config)) {
                String expectedModel = normalize(config.getDefaultModel());
                try {
                    boolean modelExists = containsModel(response.getBody(), expectedModel);
                    reachable = modelExists;
                    message = modelExists
                            ? AiProviderConstants.CONNECTION_TEST_MODEL_OK_TEMPLATE.formatted(statusCode)
                            : AiProviderConstants.CONNECTION_TEST_MODEL_NOT_FOUND_TEMPLATE.formatted(
                                    statusCode, expectedModel);
                } catch (IOException exception) {
                    reachable = false;
                    message = upstreamResponseMessage(response, AiProviderConstants.CONNECTION_TEST_MODEL_LIST_UNREADABLE);
                }
            } else if (!reachable) {
                message = upstreamResponseMessage(response, null);
            }
            return connectionResult(reachable, statusCode, message, startedAt);
        } catch (BusinessException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            log.warn("AI provider connection test failed configId={} provider={}",
                    config.getId(), config.getProviderType());
            return connectionResult(false, null, AiProviderConstants.CONNECTION_TEST_FAILED, startedAt);
        }
    }

    /** 查询并校验用户拥有的 AI 服务配置。 */
    private AiProviderConfig requireOwned(long configId, long ownerUserId) {
        return configRepository.findOwnedById(configId, ownerUserId)
                .orElseThrow(() -> new BusinessException(
                        AiProviderConfigResponseCode.CONFIG_NOT_FOUND, HttpStatus.NOT_FOUND));
    }

    /** 校验用户范围内的配置名称唯一性。 */
    private void requireUniqueName(long ownerUserId, String name, Long excludedConfigId) {
        if (configRepository.existsOwnedByName(ownerUserId, name, excludedConfigId)) {
            throw new BusinessException(AiProviderConfigResponseCode.CONFIG_NAME_CONFLICT);
        }
    }

    /** 选择首个启用配置作为默认项。 */
    private void promoteFirstEnabled(long ownerUserId, String actor, long excludedConfigId) {
        configRepository.findAllOwnedBy(ownerUserId).stream()
                .filter(config -> config.getId() != excludedConfigId)
                .filter(config -> Boolean.TRUE.equals(config.getEnabled()))
                .findFirst()
                .ifPresent(config -> configRepository.setDefault(config.getId(), ownerUserId, actor));
    }

    /** 校验并生成可持久化的代理配置字段。 */
    private ProxySaveFields proxySaveFields(
            AiProxyType proxyType,
            String proxyHost,
            Integer proxyPort,
            String proxyUsername,
            String proxyPassword,
            boolean removeProxyPassword,
            String currentProxyPasswordCiphertext,
            String currentProxyPasswordHint) {
        AiProviderProxySettings settings = proxyRuntimeSettings(
                proxyType, proxyHost, proxyPort, proxyUsername, proxyPassword);
        if (settings == null) {
            return new ProxySaveFields(AiProxyType.NONE, null, null, null, null, null);
        }
        String normalizedProxyPassword = normalize(proxyPassword);
        String proxyPasswordCiphertext = null;
        String proxyPasswordHint = null;
        if (settings.hasCredentials() && !removeProxyPassword) {
            proxyPasswordCiphertext = normalizedProxyPassword == null
                    ? currentProxyPasswordCiphertext
                    : secretCipher.encrypt(normalizedProxyPassword);
            proxyPasswordHint = normalizedProxyPassword == null
                    ? currentProxyPasswordHint
                    : maskSecret(normalizedProxyPassword);
        }
        return new ProxySaveFields(
                settings.proxyType(), settings.host(), settings.port(), settings.username(),
                proxyPasswordCiphertext, proxyPasswordHint);
    }

    /** 解析连接测试使用的代理密码。 */
    private String resolveDraftProxyPassword(
            AiProviderConnectionTestRequest request,
            AiProviderConfig savedConfig) {
        String proxyPassword = normalize(request.proxyPassword());
        if (proxyPassword != null || savedConfig == null || Boolean.TRUE.equals(request.removeProxyPassword())) {
            return proxyPassword;
        }
        if (normalize(request.proxyUsername()) == null || savedConfig.getProxyPasswordCiphertext() == null) {
            return null;
        }
        return secretCipher.decrypt(savedConfig.getProxyPasswordCiphertext());
    }

    /** 构造运行时代理设置。 */
    private AiProviderProxySettings proxyRuntimeSettings(AiProviderConfig config, String proxyPassword) {
        return proxyRuntimeSettings(
                config.getProxyType(), config.getProxyHost(), config.getProxyPort(),
                config.getProxyUsername(), proxyPassword);
    }

    /** 构造运行时代理设置。 */
    private AiProviderProxySettings proxyRuntimeSettings(
            AiProxyType proxyType,
            String proxyHost,
            Integer proxyPort,
            String proxyUsername,
            String proxyPassword) {
        AiProxyType normalizedProxyType = AiProxyType.normalize(proxyType);
        if (!normalizedProxyType.enabled()) {
            return null;
        }
        String normalizedProxyHost = normalize(proxyHost);
        if (normalizedProxyHost == null) {
            throw new BusinessException(
                    AiProviderConfigResponseCode.PROXY_CONFIG_INVALID,
                    AiProviderConstants.PROXY_HOST_REQUIRED,
                    HttpStatus.BAD_REQUEST);
        }
        if (proxyPort == null || proxyPort <= 0 || proxyPort > 65535) {
            throw new BusinessException(
                    AiProviderConfigResponseCode.PROXY_CONFIG_INVALID,
                    AiProviderConstants.PROXY_PORT_INVALID,
                    HttpStatus.BAD_REQUEST);
        }
        String normalizedProxyUsername = normalize(proxyUsername);
        String normalizedProxyPassword = normalize(proxyPassword);
        if (normalizedProxyUsername == null && normalizedProxyPassword != null) {
            throw new BusinessException(
                    AiProviderConfigResponseCode.PROXY_CONFIG_INVALID,
                    AiProviderConstants.PROXY_USERNAME_REQUIRED,
                    HttpStatus.BAD_REQUEST);
        }
        return new AiProviderProxySettings(
                normalizedProxyType,
                normalizedProxyHost,
                proxyPort,
                normalizedProxyUsername,
                normalizedProxyPassword);
    }

    /** 规范并校验 AI 服务基础地址。 */
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

    /** 将 AI 服务配置转换为脱敏响应。 */
    private AiProviderConfigResponse toResponse(AiProviderConfig config) {
        return new AiProviderConfigResponse(
                config.getId(), config.getName(), config.getProviderType(), config.getBaseUrl(),
                config.getDefaultModel(), config.getApiKeyCiphertext() != null, config.getApiKeyHint(),
                config.getProviderType().label(), config.getProviderType().description(),
                config.getProviderType().recommendedModel(), config.getProviderType().apiKeyRequired(),
                config.getProviderType().capabilities(),
                AiProxyType.normalize(config.getProxyType()), config.getProxyHost(), config.getProxyPort(),
                config.getProxyUsername(), config.getProxyPasswordCiphertext() != null, config.getProxyPasswordHint(),
                Boolean.TRUE.equals(config.getEnabled()), Boolean.TRUE.equals(config.getDefaultConfig()),
                config.getRemark(), config.getCreatedAt(), config.getUpdatedAt());
    }

    /** 判断连接测试是否需要校验模型。 */
    private boolean shouldCheckModel(AiProviderConfig config) {
        String expectedModel = normalize(config.getDefaultModel());
        if (expectedModel == null) {
            return false;
        }
        return switch (config.getProviderType()) {
            case CUSTOM, COMFYUI, NEWAPI -> false;
            default -> true;
        };
    }

    /** 检查模型列表响应是否包含目标模型。 */
    private boolean containsModel(String responseBody, String expectedModel) throws IOException {
        JsonNode root = objectMapper.readTree(responseBody);
        ModelSearchResult result = findModel(root, expectedModel);
        if (!result.modelFieldsFound()) {
            throw new IOException(AiProviderConstants.CONNECTION_TEST_MODEL_LIST_UNREADABLE);
        }
        return result.matched();
    }

    /** 递归查找响应中的目标模型。 */
    private ModelSearchResult findModel(JsonNode node, String expectedModel) {
        if (node == null || node.isNull()) {
            return ModelSearchResult.missing(false);
        }
        if (node.isArray()) {
            boolean modelFieldsFound = false;
            for (JsonNode child : node) {
                ModelSearchResult childResult = findModel(child, expectedModel);
                if (childResult.matched()) {
                    return childResult;
                }
                modelFieldsFound = modelFieldsFound || childResult.modelFieldsFound();
            }
            return ModelSearchResult.missing(modelFieldsFound);
        }
        if (node.isObject()) {
            boolean modelFieldsFound = false;
            var fields = node.fields();
            while (fields.hasNext()) {
                var field = fields.next();
                if (AiProviderConstants.MODEL_IDENTIFIER_FIELDS.contains(field.getKey()) && field.getValue().isTextual()) {
                    modelFieldsFound = true;
                    if (matchesModel(field.getValue().asText(), expectedModel)) {
                        return ModelSearchResult.found();
                    }
                }
                ModelSearchResult childResult = findModel(field.getValue(), expectedModel);
                if (childResult.matched()) {
                    return childResult;
                }
                modelFieldsFound = modelFieldsFound || childResult.modelFieldsFound();
            }
            return ModelSearchResult.missing(modelFieldsFound);
        }
        return ModelSearchResult.missing(false);
    }

    /** 判断模型标识是否匹配目标模型。 */
    private boolean matchesModel(String modelValue, String expectedModel) {
        String normalizedModelValue = modelValue.trim();
        return normalizedModelValue.equals(expectedModel)
                || normalizedModelValue.endsWith("/" + expectedModel);
    }

    /** 提取上游响应的安全提示信息。 */
    private String upstreamResponseMessage(ResponseEntity<String> response, String fallbackMessage) {
        String summary = responseBodySummary(response.getBody());
        if (summary == null) {
            summary = fallbackMessage;
        }
        if (summary == null) {
            return httpStatusMessage(response.getStatusCode().value());
        }
        return AiProviderConstants.CONNECTION_TEST_HTTP_STATUS_WITH_BODY_TEMPLATE.formatted(
                response.getStatusCode().value(), summary);
    }

    /** 生成 HTTP 状态提示。 */
    private String httpStatusMessage(int httpStatus) {
        return AiProviderConstants.CONNECTION_TEST_HTTP_STATUS_TEMPLATE.formatted(httpStatus);
    }

    /** 生成上游响应正文的安全摘要。 */
    private String responseBodySummary(String responseBody) {
        String normalizedBody = normalize(responseBody);
        if (normalizedBody == null) {
            return null;
        }
        String jsonMessage = jsonMessageSummary(normalizedBody);
        String readableText = jsonMessage == null ? htmlOrPlainTextSummary(normalizedBody) : jsonMessage;
        if (readableText == null) {
            return null;
        }
        String safeText = sanitizeSensitiveText(readableText);
        if (safeText.length() <= AiProviderConstants.CONNECTION_TEST_RESPONSE_SUMMARY_MAX_LENGTH) {
            return safeText;
        }
        return safeText.substring(0, AiProviderConstants.CONNECTION_TEST_RESPONSE_SUMMARY_MAX_LENGTH);
    }

    /** 提取 JSON 响应中的提示信息。 */
    private String jsonMessageSummary(String responseBody) {
        try {
            return findResponseMessage(objectMapper.readTree(responseBody));
        } catch (IOException exception) {
            return null;
        }
    }

    /** 递归查找响应提示字段。 */
    private String findResponseMessage(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        if (node.isTextual()) {
            return normalize(node.asText());
        }
        if (node.isArray()) {
            for (JsonNode child : node) {
                String childMessage = findResponseMessage(child);
                if (childMessage != null) {
                    return childMessage;
                }
            }
            return null;
        }
        if (node.isObject()) {
            var fields = node.fields();
            while (fields.hasNext()) {
                var field = fields.next();
                if (AiProviderConstants.RESPONSE_MESSAGE_FIELDS.contains(field.getKey())) {
                    String fieldMessage = findResponseMessage(field.getValue());
                    if (fieldMessage != null) {
                        return fieldMessage;
                    }
                }
            }
        }
        return null;
    }

    /** 提取 HTML 或纯文本响应摘要。 */
    private String htmlOrPlainTextSummary(String responseBody) {
        String withoutScripts = SCRIPT_BLOCK_PATTERN.matcher(responseBody).replaceAll(" ");
        String withoutTags = HTML_TAG_PATTERN.matcher(withoutScripts).replaceAll(" ");
        String decodedText = decodeCommonHtmlEntities(withoutTags);
        return normalize(WHITESPACE_PATTERN.matcher(decodedText).replaceAll(" "));
    }

    /** 解码常见 HTML 实体。 */
    private String decodeCommonHtmlEntities(String value) {
        return value
                .replace("&nbsp;", " ")
                .replace("&#160;", " ")
                .replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&quot;", "\"")
                .replace("&#39;", "'");
    }

    /** 清理响应文本中的敏感信息。 */
    private String sanitizeSensitiveText(String value) {
        String withoutBearerToken = BEARER_TOKEN_PATTERN.matcher(value)
                .replaceAll(AiProviderConstants.BEARER_PREFIX + AiProviderConstants.MASKED_SECRET_TEXT);
        String withoutQuerySecret = SENSITIVE_QUERY_PATTERN.matcher(withoutBearerToken)
                .replaceAll("$1" + AiProviderConstants.MASKED_SECRET_TEXT);
        return SENSITIVE_FIELD_PATTERN.matcher(withoutQuerySecret)
                .replaceAll("$1$2" + AiProviderConstants.MASKED_SECRET_TEXT);
    }

    /** 构造连接测试结果并计算耗时。 */
    private AiProviderConnectionTestResponse connectionResult(
            boolean reachable, Integer httpStatus, String message, Instant startedAt) {
        Instant testedAt = Instant.now();
        return new AiProviderConnectionTestResponse(
                reachable,
                httpStatus,
                message,
                Duration.between(startedAt, testedAt).toMillis(),
                testedAt);
    }

    /** 生成密钥掩码。 */
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

    /** 去除字符串首尾空白并处理空值。 */
    private String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    /** 描述模型列表响应中是否找到目标模型，以及响应是否像模型列表。 */
    private record ModelSearchResult(
            /** 是否找到目标模型。 */
            boolean matched,
            /** 响应中是否出现模型标识字段。 */
            boolean modelFieldsFound
    ) {

        /** 创建已找到目标模型的结果。 */
        private static ModelSearchResult found() {
            return new ModelSearchResult(true, true);
        }

        /** 创建未找到目标模型的结果。 */
        private static ModelSearchResult missing(boolean modelFieldsFound) {
            return new ModelSearchResult(false, modelFieldsFound);
        }
    }

    /** 承载经过校验和加密处理后可以写入数据库的代理配置。 */
    private record ProxySaveFields(
            /** 出站代理类型。 */
            AiProxyType proxyType,
            /** 出站代理主机。 */
            String proxyHost,
            /** 出站代理端口。 */
            Integer proxyPort,
            /** 出站代理认证用户名。 */
            String proxyUsername,
            /** AES-GCM 加密后的代理密码密文。 */
            String proxyPasswordCiphertext,
            /** 代理密码脱敏摘要。 */
            String proxyPasswordHint) {
    }

}
