package com.manga.config;

import com.manga.common.constant.OpenApiConstants;
import com.manga.config.properties.OpenApiProperties;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 配置 Manga HTTP API 的 OpenAPI 元信息和 Bearer Token 认证方案。
 */
@Configuration
public class OpenApiConfig {

    /** 创建供 OpenAPI JSON 与 Swagger UI 使用的文档模型。 */
    @Bean
    public OpenAPI mangaOpenApi(OpenApiProperties properties) {
        return new OpenAPI()
                .info(new Info()
                        .title(properties.title())
                        .description(properties.description())
                        .version(properties.version()))
                .components(new Components().addSecuritySchemes(
                        OpenApiConstants.BEARER_AUTH_SCHEME,
                        new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")));
    }
}
