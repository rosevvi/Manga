package com.manga.dto;

import com.manga.common.enums.AiProviderType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import static com.manga.common.constant.ValidationConstants.*;

/** 承载新建 AI 服务配置所需的连接信息。 */
public record AiProviderConfigCreateRequest(
        @NotBlank(message = AI_CONFIG_NAME_REQUIRED)
        @Size(max = AI_CONFIG_NAME_MAX_LENGTH, message = AI_CONFIG_NAME_TOO_LONG)
        String name,
        @NotNull(message = AI_PROVIDER_REQUIRED)
        AiProviderType providerType,
        @NotBlank(message = AI_BASE_URL_REQUIRED)
        @Size(max = AI_BASE_URL_MAX_LENGTH, message = AI_BASE_URL_TOO_LONG)
        @Pattern(regexp = "^https?://.+", message = AI_BASE_URL_INVALID)
        String baseUrl,
        @Size(max = AI_MODEL_MAX_LENGTH, message = AI_MODEL_TOO_LONG)
        String defaultModel,
        @Size(max = AI_API_KEY_MAX_LENGTH, message = AI_API_KEY_TOO_LONG)
        String apiKey,
        Boolean enabled,
        Boolean defaultConfig,
        @Size(max = AI_REMARK_MAX_LENGTH, message = AI_REMARK_TOO_LONG)
        String remark
) {
    @Override
    public String toString() {
        return "AiProviderConfigCreateRequest[name=%s, providerType=%s, apiKeyPresent=%s]"
                .formatted(name, providerType, apiKey != null && !apiKey.isBlank());
    }
}
