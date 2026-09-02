package com.manga.dto;

/** 返回已上传媒体文件的浏览器可访问地址和基础元信息。 */
public record MediaUploadResponse(
        /** 浏览器可访问的媒体地址。 */
        String url,
        /** 媒体文件存储路径。 */
        String path,
        /** 用户上传时的原始文件名。 */
        String originalFilename,
        /** 文件大小，单位为字节。 */
        long size
) {
}
