package com.manga.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** 绑定 AI 凭据加密所需的服务端密钥。 */
@ConfigurationProperties(prefix = "manga.ai.secrets")
public record AiSecretProperties(
        /** AI 凭据服务端加密密钥。 */
        String encryptionKey
) {
}
