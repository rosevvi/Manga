package com.manga.common.enums;

import com.manga.common.api.ResponseCode;

/**
 * 定义跨业务模块通用的响应码和默认提示。
 */
public enum CommonResponseCode implements ResponseCode {

    /** 请求处理成功。 */
    SUCCESS("OK", "success"),
    /** 请求参数校验失败。 */
    VALIDATION_ERROR("VALIDATION_ERROR", "请求参数校验失败"),
    /** 请求尚未认证。 */
    UNAUTHORIZED("UNAUTHORIZED", "请先登录"),
    /** 请求没有访问权限。 */
    FORBIDDEN("FORBIDDEN", "无权访问该资源"),
    /** 服务器内部处理失败。 */
    INTERNAL_ERROR("INTERNAL_ERROR", "服务器内部错误");

    /** 稳定业务编码。 */
    private final String code;
    /** 结果提示信息。 */
    private final String message;

    /** 初始化枚举项元数据。 */
    CommonResponseCode(String code, String message) {
        this.code = code;
        this.message = message;
    }

    /** 返回响应编码。 */
    @Override
    public String code() {
        return code;
    }

    /** 返回响应描述。 */
    @Override
    public String message() {
        return message;
    }
}
