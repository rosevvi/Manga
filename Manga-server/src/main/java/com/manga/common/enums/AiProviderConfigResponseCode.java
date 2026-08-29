package com.manga.common.enums;

import com.manga.common.api.ResponseCode;

/** 定义 AI 服务配置模块的业务响应码。 */
public enum AiProviderConfigResponseCode implements ResponseCode {
    CONFIG_NOT_FOUND("AI_PROVIDER_CONFIG_NOT_FOUND", "AI 服务配置不存在或无权访问"),
    CONFIG_NAME_CONFLICT("AI_PROVIDER_CONFIG_NAME_CONFLICT", "当前账号已存在同名 AI 服务配置"),
    ENCRYPTION_KEY_MISSING("AI_SECRET_ENCRYPTION_KEY_MISSING", "服务端尚未配置 AI 密钥加密密钥"),
    SECRET_DECRYPTION_FAILED("AI_SECRET_DECRYPTION_FAILED", "API Key 无法解密，请重新保存该配置"),
    BASE_URL_INVALID("AI_BASE_URL_INVALID", "API Base URL 必须是有效的 HTTP 或 HTTPS 地址");

    private final String code;
    private final String message;

    AiProviderConfigResponseCode(String code, String message) {
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
