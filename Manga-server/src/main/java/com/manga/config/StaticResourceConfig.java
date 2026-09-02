package com.manga.config;

import com.manga.config.properties.MediaUploadProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** 映射用户上传媒体文件，使前端可以通过 URL 预览图片。 */
@Configuration
@RequiredArgsConstructor
public class StaticResourceConfig implements WebMvcConfigurer {

    private final MediaUploadProperties uploadProperties;

    /** 将上传目录暴露为只读静态资源路径。 */
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String publicPattern = normalizePublicPath(uploadProperties.publicPath()) + "/**";
        String location = uploadProperties.directory().normalize().toAbsolutePath().toUri().toString();
        registry.addResourceHandler(publicPattern).addResourceLocations(location);
    }

    /** 规范静态资源公开访问路径。 */
    private String normalizePublicPath(String publicPath) {
        String normalized = publicPath == null || publicPath.isBlank() ? "/uploads" : publicPath.trim();
        if (!normalized.startsWith("/")) {
            normalized = "/" + normalized;
        }
        return normalized.endsWith("/") ? normalized.substring(0, normalized.length() - 1) : normalized;
    }
}
