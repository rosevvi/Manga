package com.manga.common.constant;

/** 维护媒体存储模块的类型、默认值和业务消息。 */
public final class StorageConstants {

    /** 本地磁盘存储策略类型。 */
    public static final String LOCAL_TYPE = "local";
    /** 阿里云 OSS 存储策略类型。 */
    public static final String ALIYUN_OSS_TYPE = "aliyun-oss";
    /** 默认媒体上传目录。 */
    public static final String DEFAULT_UPLOAD_DIRECTORY = "uploads";
    /** 默认媒体公开访问路径。 */
    public static final String DEFAULT_PUBLIC_PATH = "/uploads";
    /** 默认图片存储子目录。 */
    public static final String DEFAULT_IMAGE_SUB_DIR = "images";
    /** 默认 OSS Bucket 名称。 */
    public static final String DEFAULT_OSS_BUCKET_NAME = "manga-ai";
    /** 默认 OSS 对象路径前缀。 */
    public static final String DEFAULT_OSS_OBJECT_PREFIX = "mannga";
    /** 存储策略不可用提示。 */
    public static final String STORAGE_STRATEGY_UNAVAILABLE = "没有可用的媒体存储策略";
    /** 媒体文件保存失败提示。 */
    public static final String STORAGE_FILE_SAVE_FAILED = "媒体文件保存失败";
    /** 阿里云 OSS 配置不完整提示。 */
    public static final String ALIYUN_OSS_CONFIGURATION_INCOMPLETE =
            "阿里云 OSS 配置不完整，请填写 Endpoint、Bucket、AccessKey ID 和 AccessKey Secret";
    /** 阿里云 OSS Endpoint 无效提示。 */
    public static final String ALIYUN_OSS_ENDPOINT_INVALID = "阿里云 OSS Endpoint 格式无效";

    /** 禁止实例化常量类。 */
    private StorageConstants() {
    }
}
