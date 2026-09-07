package com.manga.common.constant;

import java.util.Map;
import java.util.Set;

/** 维护用户上传媒体文件的校验规则和访问路径。 */
public final class MediaUploadConstants {

    /** 上传图片为空时的提示。 */
    public static final String UPLOAD_IMAGE_EMPTY = "上传图片不能为空";
    /** 上传图片超出大小限制提示模板。 */
    public static final String UPLOAD_IMAGE_TOO_LARGE = "上传图片不能超过 %s";
    /** 上传请求超出大小限制提示。 */
    public static final String UPLOAD_REQUEST_TOO_LARGE = "上传文件过大，请压缩后重试";
    /** 导入文件为空提示。 */
    public static final String UPLOAD_SCRIPT_EMPTY = "导入文件不能为空";
    /** 导入文件类型不受支持提示。 */
    public static final String UPLOAD_SCRIPT_TYPE_UNSUPPORTED = "仅支持 TXT 或 Markdown 文本文件";
    /** 导入文件编码不受支持提示。 */
    public static final String UPLOAD_SCRIPT_ENCODING_UNSUPPORTED = "导入文件必须使用 UTF-8 编码";
    /** 上传图片类型不受支持提示。 */
    public static final String UPLOAD_IMAGE_TYPE_UNSUPPORTED = "仅支持 JPG、PNG、WebP 和 GIF 图片";
    /** 图片上传失败提示。 */
    public static final String UPLOAD_IMAGE_FAILED = "图片上传失败，请稍后重试";
    /** 上传子目录合法格式。 */
    public static final String UPLOAD_SUB_DIR_PATTERN = "[A-Za-z0-9/_-]{1,80}";
    /** 上传子目录无效提示。 */
    public static final String UPLOAD_SUB_DIR_INVALID = "上传目录格式不正确";
    /** 允许上传的图片 MIME 类型集合。 */
    public static final Set<String> SUPPORTED_IMAGE_CONTENT_TYPES = Set.of(
            "image/jpeg",
            "image/jpg",
            "image/png",
            "image/webp",
            "image/gif"
    );
    /** 图片 MIME 类型与扩展名映射。 */
    public static final Map<String, String> IMAGE_EXTENSION_BY_CONTENT_TYPE = Map.of(
            "image/jpeg", ".jpg",
            "image/jpg", ".jpg",
            "image/png", ".png",
            "image/webp", ".webp",
            "image/gif", ".gif"
    );

    /** 禁止实例化常量类。 */
    private MediaUploadConstants() {
    }
}
