package com.manga.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 绑定 Manga OpenAPI 文档的展示元信息。
 */
@ConfigurationProperties(prefix = "manga.openapi")
public record OpenApiProperties(
        /** API 文档标题。 */
        String title,
        /** API 文档说明。 */
        String description,
        /** 对外接口版本。 */
        String version
) {
}
