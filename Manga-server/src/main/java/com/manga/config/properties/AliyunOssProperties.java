package com.manga.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

import static com.manga.common.constant.StorageConstants.DEFAULT_OSS_BUCKET_NAME;
import static com.manga.common.constant.StorageConstants.DEFAULT_OSS_OBJECT_PREFIX;

/** 绑定阿里云 OSS 上传凭据、对象前缀和公开访问地址。 */
@ConfigurationProperties(prefix = "manga.storage.aliyun-oss")
public record AliyunOssProperties(
        /** 配置是否启用。 */
        boolean enabled,
        /** 阿里云 OSS 地域 Endpoint。 */
        String endpoint,
        /** OSS Bucket 名称。 */
        String bucketName,
        /** 阿里云 RAM 用户 AccessKey ID。 */
        String accessKeyId,
        /** 阿里云 RAM 用户 AccessKey Secret。 */
        String accessKeySecret,
        /** OSS 对象统一路径前缀。 */
        String objectPrefix,
        /** OSS 自定义域名或 CDN 域名。 */
        String publicDomain
) {

    /** 规范可公开配置并保留未填写的凭据占位。 */
    public AliyunOssProperties {
        endpoint = normalize(endpoint);
        bucketName = defaultIfBlank(bucketName, DEFAULT_OSS_BUCKET_NAME);
        accessKeyId = normalize(accessKeyId);
        accessKeySecret = normalize(accessKeySecret);
        objectPrefix = defaultIfBlank(objectPrefix, DEFAULT_OSS_OBJECT_PREFIX);
        publicDomain = normalize(publicDomain);
    }

    /** 判断建立 OSS 客户端所需配置是否完整。 */
    public boolean configured() {
        return enabled && endpoint != null && bucketName != null && accessKeyId != null && accessKeySecret != null;
    }

    /** 返回不包含 AccessKey 的安全配置摘要。 */
    @Override
    public String toString() {
        return "AliyunOssProperties[enabled=" + enabled
                + ", endpoint=" + endpoint
                + ", bucketName=" + bucketName
                + ", accessKeyIdConfigured=" + (accessKeyId != null)
                + ", accessKeySecretConfigured=" + (accessKeySecret != null)
                + ", objectPrefix=" + objectPrefix
                + ", publicDomain=" + publicDomain + "]";
    }

    /** 返回已去除首尾空白的文本，空文本转换为 null。 */
    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    /** 为空文本提供默认值。 */
    private static String defaultIfBlank(String value, String defaultValue) {
        String normalized = normalize(value);
        return normalized == null ? defaultValue : normalized;
    }
}
