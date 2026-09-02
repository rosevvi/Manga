package com.manga.common.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.manga.common.enums.CommonResponseCode;

import java.time.Instant;

/**
 * 统一封装接口成功或失败时返回给客户端的数据。
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiResponse<T>(
        boolean success,
        String code,
        String message,
        T data,
        Instant timestamp
) {

    /**
     * 创建携带数据的成功响应。
     */
    public static <T> ApiResponse<T> success(T data) {
        return success(CommonResponseCode.SUCCESS.message(), data);
    }

    /**
     * 创建带自定义提示和数据的成功响应。
     */
    public static <T> ApiResponse<T> success(String message, T data) {
        return new ApiResponse<>(true, CommonResponseCode.SUCCESS.code(), message, data, Instant.now());
    }

    /**
     * 根据标准错误码创建失败响应。
     */
    public static ApiResponse<Void> failure(ResponseCode responseCode) {
        return failure(responseCode, responseCode.message(), null);
    }

    /**
     * 根据标准错误码和动态提示创建失败响应。
     */
    public static ApiResponse<Void> failure(ResponseCode responseCode, String message) {
        return failure(responseCode, message, null);
    }

    /**
     * 创建携带错误详情的失败响应。
     */
    public static <T> ApiResponse<T> failure(ResponseCode responseCode, T data) {
        return failure(responseCode, responseCode.message(), data);
    }

    /** 构造失败响应并保留可选响应数据。 */
    private static <T> ApiResponse<T> failure(ResponseCode responseCode, String message, T data) {
        return new ApiResponse<>(false, responseCode.code(), message, data, Instant.now());
    }
}
