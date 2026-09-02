package com.manga.common.enums;

import com.manga.common.api.ResponseCode;

/** 定义 AI 服务配置模块的业务响应码。 */
public enum AiProviderConfigResponseCode implements ResponseCode {
    /** AI 服务配置不存在或无权访问。 */
    CONFIG_NOT_FOUND("AI_PROVIDER_CONFIG_NOT_FOUND", "AI 服务配置不存在或无权访问"),
    /** AI 服务配置名称冲突。 */
    CONFIG_NAME_CONFLICT("AI_PROVIDER_CONFIG_NAME_CONFLICT", "当前账号已存在同名 AI 服务配置"),
    /** 服务端缺少 AI 凭据加密密钥。 */
    ENCRYPTION_KEY_MISSING("AI_SECRET_ENCRYPTION_KEY_MISSING", "服务端尚未配置 AI 密钥加密密钥"),
    /** AI 凭据密文解密失败。 */
    SECRET_DECRYPTION_FAILED("AI_SECRET_DECRYPTION_FAILED", "API Key 无法解密，请重新保存该配置"),
    /** AI 服务基础地址无效。 */
    BASE_URL_INVALID("AI_BASE_URL_INVALID", "API Base URL 必须是有效的 HTTP 或 HTTPS 地址"),
    /** AI 出站代理配置无效。 */
    PROXY_CONFIG_INVALID("AI_PROXY_CONFIG_INVALID", "代理配置不正确");

    /** 稳定业务编码。 */
    private final String code;
    /** 结果提示信息。 */
    private final String message;

    /** 初始化枚举项元数据。 */
    AiProviderConfigResponseCode(String code, String message) {
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
