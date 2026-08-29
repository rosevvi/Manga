package com.manga.common.enums;

import com.manga.common.api.ResponseCode;

/**
 * 定义跨业务模块通用的响应码和默认提示。
 */
public enum CommonResponseCode implements ResponseCode {

    SUCCESS("OK", "success"),
    VALIDATION_ERROR("VALIDATION_ERROR", "请求参数校验失败"),
    UNAUTHORIZED("UNAUTHORIZED", "请先登录"),
    FORBIDDEN("FORBIDDEN", "无权访问该资源"),
    INTERNAL_ERROR("INTERNAL_ERROR", "服务器内部错误");

    private final String code;
    private final String message;

    CommonResponseCode(String code, String message) {
        this.code = code;
        this.message = message;
    }

    @Override
    public String code() {
        return code;
    }

    @Override
    public String message() {
        return message;
    }
}
