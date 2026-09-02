package com.manga.common.enums;

import java.util.Locale;

/** 定义 AI 服务出站代理类型。 */
public enum AiProxyType {
    /** 不使用出站代理。 */
    NONE,
    /** 使用 HTTP 出站代理。 */
    HTTP,
    /** 使用 SOCKS5 出站代理。 */
    SOCKS5;

    /** 将空代理类型规范为不使用代理。 */
    public static AiProxyType normalize(AiProxyType proxyType) {
        return proxyType == null ? NONE : proxyType;
    }

    /** 将文本转换为代理类型。 */
    public static AiProxyType fromText(String value) {
        if (value == null || value.isBlank()) {
            return NONE;
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT).replace("-", "").replace("_", "");
        return switch (normalized) {
            case "HTTP", "HTTPS" -> HTTP;
            case "SOCKS", "SOCKS5" -> SOCKS5;
            case "NONE", "OFF", "DISABLED" -> NONE;
            default -> throw new IllegalArgumentException(value);
        };
    }

    /** 判断当前代理类型是否启用代理。 */
    public boolean enabled() {
        return this != NONE;
    }
}
