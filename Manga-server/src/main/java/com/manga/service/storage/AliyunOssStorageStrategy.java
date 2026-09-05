package com.manga.service.storage;

import com.aliyun.oss.ClientException;
import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import com.aliyun.oss.OSSException;
import com.aliyun.oss.model.ObjectMetadata;
import com.manga.config.properties.AliyunOssProperties;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;

import static com.manga.common.constant.StorageConstants.ALIYUN_OSS_TYPE;
import static com.manga.common.constant.StorageConstants.ALIYUN_OSS_CONFIGURATION_INCOMPLETE;
import static com.manga.common.constant.StorageConstants.ALIYUN_OSS_ENDPOINT_INVALID;
import static com.manga.common.constant.StorageConstants.DEFAULT_UPLOAD_DIRECTORY;
import static com.manga.common.constant.StorageConstants.STORAGE_FILE_SAVE_FAILED;

/** 使用阿里云官方 SDK 将媒体文件保存到 OSS。 */
@Component
@Slf4j
@RequiredArgsConstructor
public class AliyunOssStorageStrategy implements StorageStrategy {

    /** 对象路径片段中需要移除的不安全字符规则。 */
    private static final Pattern UNSAFE_PATH_CHARACTER_PATTERN = Pattern.compile("[^A-Za-z0-9._-]");

    private final AliyunOssProperties properties;
    /** 延迟创建并在应用生命周期内复用的 OSS 客户端。 */
    private volatile OSS client;

    /** 返回阿里云 OSS 存储策略类型。 */
    @Override
    public String getType() {
        return ALIYUN_OSS_TYPE;
    }

    /** 上传媒体数据并返回浏览器可访问的对象地址。 */
    @Override
    public String storeBytes(byte[] data, String subDir, String extension) {
        requireConfigured();
        String objectKey = buildObjectKey(subDir, extension);
        ObjectMetadata metadata = new ObjectMetadata();
        metadata.setContentLength(data.length);
        metadata.setContentType(resolveContentType(extension));

        try {
            ByteArrayInputStream inputStream = new ByteArrayInputStream(data);
            ossClient().putObject(properties.bucketName(), objectKey, inputStream, metadata);
            log.info("[AliyunOssStorageStrategy#storeBytes] stored bucket={} key={} size={}",
                    properties.bucketName(), objectKey, data.length);
            return resolvePublicUrl(objectKey);
        } catch (OSSException exception) {
            log.warn("[AliyunOssStorageStrategy#storeBytes] OSS rejected upload errorCode={} requestId={}",
                    exception.getErrorCode(), exception.getRequestId());
            throw new IllegalStateException(STORAGE_FILE_SAVE_FAILED, exception);
        } catch (ClientException exception) {
            log.warn("[AliyunOssStorageStrategy#storeBytes] OSS client upload failed bucket={} key={}",
                    properties.bucketName(), objectKey);
            throw new IllegalStateException(STORAGE_FILE_SAVE_FAILED, exception);
        }
    }

    /** 关闭复用的 OSS 客户端连接。 */
    @PreDestroy
    public void closeClient() {
        OSS current = client;
        if (current != null) {
            current.shutdown();
        }
    }

    /** 延迟创建并复用线程安全的 OSS 客户端。 */
    private OSS ossClient() {
        OSS current = client;
        if (current != null) {
            return current;
        }
        synchronized (this) {
            if (client == null) {
                client = new OSSClientBuilder().build(
                        normalizeEndpoint(properties.endpoint()),
                        properties.accessKeyId(),
                        properties.accessKeySecret());
            }
            return client;
        }
    }

    /** 校验建立 OSS 客户端所需的环境配置。 */
    private void requireConfigured() {
        if (!properties.configured()) {
            throw new IllegalStateException(ALIYUN_OSS_CONFIGURATION_INCOMPLETE);
        }
        normalizeEndpoint(properties.endpoint());
    }

    /** 生成包含统一对象前缀和业务子目录的对象键。 */
    private String buildObjectKey(String subDir, String extension) {
        String prefix = normalizePath(properties.objectPrefix(), properties.objectPrefix());
        String directory = normalizePath(subDir, DEFAULT_UPLOAD_DIRECTORY);
        return prefix + "/" + directory + "/" + UUID.randomUUID() + normalizeExtension(extension);
    }

    /** 清理对象路径中的不安全片段。 */
    private String normalizePath(String value, String defaultValue) {
        String normalized = value == null || value.isBlank() ? defaultValue : value.trim();
        StringBuilder result = new StringBuilder();
        for (String part : normalized.replace('\\', '/').split("/")) {
            String safePart = UNSAFE_PATH_CHARACTER_PATTERN.matcher(part).replaceAll("");
            if (safePart.isBlank() || ".".equals(safePart) || "..".equals(safePart)) {
                continue;
            }
            if (!result.isEmpty()) {
                result.append('/');
            }
            result.append(safePart);
        }
        return result.isEmpty() ? defaultValue : result.toString();
    }

    /** 规范上传文件扩展名。 */
    private String normalizeExtension(String extension) {
        if (extension == null || extension.isBlank()) {
            return ".bin";
        }
        String normalized = extension.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
        return normalized.isBlank() ? ".bin" : "." + normalized;
    }

    /** 根据扩展名设置 OSS 对象内容类型。 */
    private String resolveContentType(String extension) {
        return switch (normalizeExtension(extension)) {
            case ".png" -> "image/png";
            case ".jpg", ".jpeg" -> "image/jpeg";
            case ".gif" -> "image/gif";
            case ".webp" -> "image/webp";
            case ".svg" -> "image/svg+xml";
            case ".mp4" -> "video/mp4";
            case ".webm" -> "video/webm";
            case ".mov" -> "video/quicktime";
            default -> "application/octet-stream";
        };
    }

    /** 生成自定义域名或 Bucket 默认域名下的公开地址。 */
    private String resolvePublicUrl(String objectKey) {
        String configuredDomain = properties.publicDomain();
        String baseUrl = configuredDomain == null || configuredDomain.isBlank()
                ? defaultPublicDomain()
                : normalizeDomain(configuredDomain);
        return baseUrl + "/" + encodeObjectKey(objectKey);
    }

    /** 根据 Endpoint 推导 Bucket 默认公开域名。 */
    private String defaultPublicDomain() {
        URI endpoint = URI.create(normalizeEndpoint(properties.endpoint()));
        return endpoint.getScheme() + "://" + properties.bucketName() + "." + endpoint.getAuthority();
    }

    /** 规范 OSS Endpoint 并校验协议和主机名。 */
    private String normalizeEndpoint(String endpoint) {
        try {
            String normalized = endpoint.startsWith("http://") || endpoint.startsWith("https://")
                    ? endpoint
                    : "https://" + endpoint;
            URI uri = URI.create(normalized.replaceAll("/+$", ""));
            boolean supportedScheme = "http".equalsIgnoreCase(uri.getScheme())
                    || "https".equalsIgnoreCase(uri.getScheme());
            boolean rootPath = uri.getPath() == null || uri.getPath().isBlank() || "/".equals(uri.getPath());
            if (uri.getHost() == null || !supportedScheme || !rootPath
                    || uri.getQuery() != null || uri.getFragment() != null) {
                throw new IllegalArgumentException(ALIYUN_OSS_ENDPOINT_INVALID);
            }
            return uri.toString();
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException(ALIYUN_OSS_ENDPOINT_INVALID, exception);
        }
    }

    /** 为自定义公开域名补充协议并移除尾部斜杠。 */
    private String normalizeDomain(String domain) {
        String normalized = domain.trim();
        if (!normalized.startsWith("http://") && !normalized.startsWith("https://")) {
            normalized = "https://" + normalized;
        }
        return normalized.replaceAll("/+$", "");
    }

    /** 对对象键的每个路径片段进行 URL 编码。 */
    private String encodeObjectKey(String objectKey) {
        return String.join("/", Arrays.stream(objectKey.split("/"))
                .map(part -> URLEncoder.encode(part, StandardCharsets.UTF_8).replace("+", "%20"))
                .toList());
    }
}
