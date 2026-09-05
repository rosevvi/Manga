package com.manga.service;

import com.manga.common.enums.CommonResponseCode;
import com.manga.common.exception.BusinessException;
import com.manga.config.properties.MediaUploadProperties;
import com.manga.dto.MediaUploadResponse;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.io.IOException;
import java.util.Locale;
import java.util.regex.Pattern;

import static com.manga.common.constant.MediaUploadConstants.*;
import static com.manga.common.constant.StorageConstants.DEFAULT_IMAGE_SUB_DIR;

/** 保存用户上传图片，并生成前端可直接展示的访问地址。 */
@Service
@RequiredArgsConstructor
public class MediaUploadService {

    /** 合法上传子目录格式。 */
    private static final Pattern SUB_DIR_PATTERN = Pattern.compile(UPLOAD_SUB_DIR_PATTERN);

    private final MediaUploadProperties uploadProperties;
    private final MediaStorageService mediaStorageService;

    /** 校验并保存图片文件，返回绝对访问 URL 与站内路径。 */
    public MediaUploadResponse uploadImage(MultipartFile file, String subDir, HttpServletRequest request) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(CommonResponseCode.VALIDATION_ERROR, UPLOAD_IMAGE_EMPTY, HttpStatus.BAD_REQUEST);
        }
        if (file.getSize() > uploadProperties.maxImageSize().toBytes()) {
            throw new BusinessException(CommonResponseCode.VALIDATION_ERROR,
                    UPLOAD_IMAGE_TOO_LARGE.formatted(uploadProperties.maxImageSize()), HttpStatus.BAD_REQUEST);
        }

        String contentType = normalizeContentType(file.getContentType());
        if (!SUPPORTED_IMAGE_CONTENT_TYPES.contains(contentType)) {
            throw new BusinessException(
                    CommonResponseCode.VALIDATION_ERROR, UPLOAD_IMAGE_TYPE_UNSUPPORTED, HttpStatus.BAD_REQUEST);
        }

        String extension = IMAGE_EXTENSION_BY_CONTENT_TYPE.get(contentType);
        try {
            String path = mediaStorageService.storeBytes(file.getBytes(), normalizeSubDir(subDir), extension);
            String url = toAbsoluteUrl(path, request);
            return new MediaUploadResponse(url, path, file.getOriginalFilename(), file.getSize());
        } catch (IOException | IllegalArgumentException | IllegalStateException exception) {
            throw new BusinessException(
                    CommonResponseCode.INTERNAL_ERROR, UPLOAD_IMAGE_FAILED, HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    /** 校验并规范上传子目录。 */
    private String normalizeSubDir(String subDir) {
        String normalized = subDir == null || subDir.isBlank() ? DEFAULT_IMAGE_SUB_DIR : subDir.trim();
        if (!SUB_DIR_PATTERN.matcher(normalized).matches() || normalized.contains("..")) {
            throw new BusinessException(CommonResponseCode.VALIDATION_ERROR, UPLOAD_SUB_DIR_INVALID,
                    HttpStatus.BAD_REQUEST);
        }
        return normalized;
    }

    /** 规范上传文件内容类型。 */
    private String normalizeContentType(String contentType) {
        if (contentType == null) {
            return "";
        }
        int separator = contentType.indexOf(';');
        String value = separator >= 0 ? contentType.substring(0, separator) : contentType;
        return value.trim().toLowerCase(Locale.ROOT);
    }

    /** 将站内媒体路径转换为绝对访问地址。 */
    private String toAbsoluteUrl(String path, HttpServletRequest request) {
        if (path.startsWith("http://") || path.startsWith("https://")) {
            return path;
        }
        return ServletUriComponentsBuilder.fromRequestUri(request)
                .replacePath(path)
                .replaceQuery(null)
                .build()
                .toUriString();
    }
}
