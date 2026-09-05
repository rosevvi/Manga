package com.manga.integration.http;

/**
 * 描述外部 HTTP 请求运行时使用的代理配置。
 */
public record ExternalProxySettings(
        /** 代理类型。 */
        ExternalProxyType proxyType,
        /** 代理主机。 */
        String host,
        /** 代理端口。 */
        int port,
        /** 代理用户名。 */
        String username,
        /** 代理密码明文，仅在请求构造阶段使用。 */
        String password
) {

    /** 判断是否启用代理。 */
    public boolean enabled() {
        return proxyType != null && proxyType != ExternalProxyType.NONE;
    }

    /** 判断是否配置了完整的代理认证信息。 */
    public boolean hasCredentials() {
        return username != null && !username.isBlank()
                && password != null && !password.isBlank();
    }
}
