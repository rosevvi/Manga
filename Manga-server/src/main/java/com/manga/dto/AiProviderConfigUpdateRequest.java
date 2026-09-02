package com.manga.dto;

import com.manga.common.enums.AiProviderType;
import com.manga.common.enums.AiProxyType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import static com.manga.common.constant.ValidationConstants.*;

/** 承载 AI 服务配置更新，并支持显式清除已保存密钥。 */
    public record AiProviderConfigUpdateRequest(
        /** AI 服务配置名称。 */
        @NotBlank(message = AI_CONFIG_NAME_REQUIRED)
        @Size(max = AI_CONFIG_NAME_MAX_LENGTH, message = AI_CONFIG_NAME_TOO_LONG)
        String name,
        /** AI 服务商类型。 */
        @NotNull(message = AI_PROVIDER_REQUIRED)
        AiProviderType providerType,
        /** AI 服务基础地址。 */
        @NotBlank(message = AI_BASE_URL_REQUIRED)
        @Size(max = AI_BASE_URL_MAX_LENGTH, message = AI_BASE_URL_TOO_LONG)
        @Pattern(regexp = "^https?://.+", message = AI_BASE_URL_INVALID)
        String baseUrl,
        /** 默认模型编码。 */
        @Size(max = AI_MODEL_MAX_LENGTH, message = AI_MODEL_TOO_LONG)
        String defaultModel,
        /** AI 服务 API Key 明文，仅在请求处理中短暂使用。 */
        @Size(max = AI_API_KEY_MAX_LENGTH, message = AI_API_KEY_TOO_LONG)
        String apiKey,
        /** 是否移除已保存的 API Key。 */
        Boolean removeApiKey,
        /** 出站代理类型。 */
        AiProxyType proxyType,
        /** 出站代理主机。 */
        @Size(max = AI_PROXY_HOST_MAX_LENGTH, message = AI_PROXY_HOST_TOO_LONG)
        String proxyHost,
        /** 出站代理端口。 */
        @Min(value = 1, message = AI_PROXY_PORT_INVALID)
        @Max(value = 65535, message = AI_PROXY_PORT_INVALID)
        Integer proxyPort,
        /** 出站代理认证用户名。 */
        @Size(max = AI_PROXY_USERNAME_MAX_LENGTH, message = AI_PROXY_USERNAME_TOO_LONG)
        String proxyUsername,
        /** 出站代理密码明文，仅在请求处理中短暂使用。 */
        @Size(max = AI_PROXY_PASSWORD_MAX_LENGTH, message = AI_PROXY_PASSWORD_TOO_LONG)
        String proxyPassword,
        /** 是否移除已保存的代理密码。 */
        Boolean removeProxyPassword,
        /** 配置是否启用。 */
        Boolean enabled,
        /** 是否为默认配置。 */
        Boolean defaultConfig,
        /** 配置备注。 */
        @Size(max = AI_REMARK_MAX_LENGTH, message = AI_REMARK_TOO_LONG)
        String remark
) {
    /** 返回不包含密钥明文的日志字符串。 */
    @Override
    public String toString() {
        return "AiProviderConfigUpdateRequest[name=%s, providerType=%s, apiKeyChanged=%s]"
                .formatted(name, providerType,
                        (apiKey != null && !apiKey.isBlank()) || Boolean.TRUE.equals(removeApiKey));
    }
}
