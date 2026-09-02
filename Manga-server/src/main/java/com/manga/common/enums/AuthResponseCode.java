package com.manga.common.enums;

import com.manga.common.api.ResponseCode;

/**
 * 定义认证与用户角色模块的业务响应码。
 */
public enum AuthResponseCode implements ResponseCode {
    /** 用户名或密码错误。 */
    INVALID_CREDENTIALS("INVALID_CREDENTIALS", "用户名或密码错误"),
    /** 账号当前不可用。 */
    ACCOUNT_UNAVAILABLE("ACCOUNT_UNAVAILABLE", "账号当前不可用"),
    /** 用户不存在。 */
    USER_NOT_FOUND("USER_NOT_FOUND", "用户不存在"),
    /** 当前密码校验失败。 */
    CURRENT_PASSWORD_INVALID("CURRENT_PASSWORD_INVALID", "当前密码不正确"),
    /** 游客资料只读。 */
    GUEST_PROFILE_READ_ONLY("GUEST_PROFILE_READ_ONLY", "游客身份不能修改账号资料"),
    /** 微信公众号配置不可用。 */
    WECHAT_CONFIGURATION_UNAVAILABLE("WECHAT_CONFIGURATION_UNAVAILABLE", "微信公众号登录配置不完整"),
    /** 微信服务暂时不可用。 */
    WECHAT_SERVICE_UNAVAILABLE("WECHAT_SERVICE_UNAVAILABLE", "微信服务暂时不可用"),
    /** 微信回调请求无效。 */
    WECHAT_CALLBACK_INVALID("WECHAT_CALLBACK_INVALID", "微信回调请求无效"),
    /** 外部登录会话不存在。 */
    EXTERNAL_LOGIN_NOT_FOUND("EXTERNAL_LOGIN_NOT_FOUND", "登录会话不存在"),
    /** 角色编码不可分配。 */
    INVALID_ROLE("INVALID_ROLE", "包含不可分配的角色");

    /** 稳定业务编码。 */
    private final String code;
    /** 结果提示信息。 */
    private final String message;

    /** 初始化枚举项元数据。 */
    AuthResponseCode(String code, String message) {
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
