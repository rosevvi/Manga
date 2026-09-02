package com.manga.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;

import java.nio.file.Path;

/** 绑定用户上传媒体文件的保存目录、访问路径和大小限制。 */
@ConfigurationProperties(prefix = "manga.upload")
public record MediaUploadProperties(
        /** 本地上传文件保存目录。 */
        Path directory,
        /** 媒体文件公开访问路径前缀。 */
        String publicPath,
        /** 允许上传的最大图片大小。 */
        DataSize maxImageSize
) {

    public MediaUploadProperties {
        if (directory == null) {
            directory = Path.of("uploads");
        }
        if (publicPath == null || publicPath.isBlank()) {
            publicPath = "/uploads";
        }
        if (maxImageSize == null) {
            maxImageSize = DataSize.ofMegabytes(8);
        }
    }
}
