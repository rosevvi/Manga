package com.manga.controller;

import com.manga.common.api.ApiResponse;
import com.manga.common.security.SecurityUtils;
import com.manga.dto.MediaUploadResponse;
import com.manga.service.MediaUploadService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** 提供受认证的创作图片上传接口。 */
@RestController
@RequestMapping("/api/v1/uploads")
@Slf4j
@RequiredArgsConstructor
public class MediaUploadController {

    private final MediaUploadService uploadService;

    /** 接收图片上传并返回可访问地址。 */
    @PostMapping("/images")
    public ApiResponse<MediaUploadResponse> uploadImage(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "subDir", required = false) String subDir,
            HttpServletRequest request) {
        log.info("[MediaUploadController#uploadImage] request subject={} subDir={} contentType={} size={}",
                SecurityUtils.getCurrentUsername(), subDir, file.getContentType(), file.getSize());
        MediaUploadResponse result = uploadService.uploadImage(file, subDir, request);
        log.info("[MediaUploadController#uploadImage] response path={} size={}", result.path(), result.size());
        return ApiResponse.success(result);
    }
}
