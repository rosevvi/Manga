package com.manga.service.storage;

/** 受平台支持的物理生成产物，其扩展名和 MIME 类型固定。 */
public enum GeneratedMediaFormat {
    /** 静态分镜图。 */
    PNG("png", "image/png"),
    /** 镜头视频。 */
    MP4("mp4", "video/mp4");

    private final String extension;
    private final String mimeType;

    GeneratedMediaFormat(String extension, String mimeType) {
        this.extension = extension;
        this.mimeType = mimeType;
    }

    /** 返回文件扩展名。 */
    public String extension() { return extension; }

    /** 返回产物使用的媒体类型。 */
    public String mimeType() { return mimeType; }
}
