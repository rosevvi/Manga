package com.manga.service.storage;

import com.manga.config.properties.MediaUploadProperties;
import com.manga.entity.StorageConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static com.manga.common.constant.StorageConstants.LOCAL_TYPE;
import static com.manga.common.constant.StorageConstants.DEFAULT_UPLOAD_DIRECTORY;
import static com.manga.common.constant.StorageConstants.STORAGE_FILE_SAVE_FAILED;

/** 将媒体文件保存到后端本地磁盘，并通过静态资源路径访问。 */
@Component
@Slf4j
@RequiredArgsConstructor
public class LocalStorageStrategy implements StorageStrategy {

    private final MediaUploadProperties uploadProperties;

    /** 返回本地存储策略类型。 */
    @Override
    public String getType() {
        return LOCAL_TYPE;
    }

    /** 将媒体二进制数据保存到本地磁盘。 */
    @Override
    public String storeBytes(byte[] data, String subDir, String extension, StorageConfig config) {
        Path baseDirectory = resolveBaseDirectory(config);
        String filename = UUID.randomUUID() + normalizeExtension(extension);
        Path targetDirectory = baseDirectory.resolve(normalizeSubDir(subDir)).normalize().toAbsolutePath();
        Path targetPath = targetDirectory.resolve(filename).normalize();
        if (!targetPath.startsWith(targetDirectory)) {
            throw new IllegalArgumentException(STORAGE_FILE_SAVE_FAILED);
        }

        try {
            Files.createDirectories(targetDirectory);
            Files.write(targetPath, data);
            log.info("[LocalStorageStrategy#storeBytes] stored path={} size={}", targetPath, data.length);
            return normalizePublicPath(config) + "/" + normalizeSubDir(subDir) + "/" + filename;
        } catch (IOException exception) {
            throw new IllegalStateException(STORAGE_FILE_SAVE_FAILED, exception);
        }
    }

    /** 解析本地存储根目录。 */
    private Path resolveBaseDirectory(StorageConfig config) {
        if (config != null && config.getBasePath() != null && !config.getBasePath().isBlank()) {
            return Path.of(config.getBasePath()).normalize().toAbsolutePath();
        }
        return uploadProperties.directory().normalize().toAbsolutePath();
    }

    /** 规范媒体公开访问路径。 */
    private String normalizePublicPath(StorageConfig config) {
        String configured = config != null && config.getCustomDomain() != null && !config.getCustomDomain().isBlank()
                ? config.getCustomDomain()
                : config != null && config.getPublicPath() != null && !config.getPublicPath().isBlank()
                ? config.getPublicPath()
                : uploadProperties.publicPath();
        String normalized = configured.trim().replaceAll("/+$", "");
        if (normalized.startsWith("http://") || normalized.startsWith("https://")) {
            return normalized;
        }
        return normalized.startsWith("/") ? normalized : "/" + normalized;
    }

    /** 规范媒体存储子目录。 */
    private String normalizeSubDir(String subDir) {
        String normalized = subDir == null || subDir.isBlank() ? DEFAULT_UPLOAD_DIRECTORY : subDir.trim();
        return normalized.replace("\\", "/").replaceAll("^/+", "").replaceAll("/+$", "");
    }

    /** 规范媒体文件扩展名。 */
    private String normalizeExtension(String extension) {
        if (extension == null || extension.isBlank()) {
            return ".bin";
        }
        return extension.startsWith(".") ? extension : "." + extension;
    }
}
