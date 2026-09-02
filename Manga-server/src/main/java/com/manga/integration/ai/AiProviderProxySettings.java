package com.manga.integration.ai;

import com.manga.common.enums.AiProxyType;

import java.net.InetSocketAddress;
import java.net.Proxy;

/** 描述 AI 服务出站请求运行时使用的代理配置。 */
public record AiProviderProxySettings(
        /** 出站代理类型。 */
        AiProxyType proxyType,
        /** 代理服务器主机。 */
        String host,
        /** 代理服务器端口。 */
        int port,
        /** 用户名。 */
        String username,
        /** 密码明文，仅在当前请求或初始化过程中使用。 */
        String password
) {

    /** 判断代理认证凭据是否完整。 */
    public boolean hasCredentials() {
        return username != null && !username.isBlank();
    }

    /** 返回代理密码或空字符串。 */
    public String passwordOrEmpty() {
        return password == null ? "" : password;
    }

    /** 转换为 Java 网络代理对象。 */
    public Proxy toJavaProxy() {
        Proxy.Type javaType = proxyType == AiProxyType.HTTP ? Proxy.Type.HTTP : Proxy.Type.SOCKS;
        return new Proxy(javaType, new InetSocketAddress(host, port));
    }

    /** 返回不包含代理密码的日志字符串。 */
    @Override
    public String toString() {
        return "AiProviderProxySettings[proxyType=%s, host=%s, port=%s, usernamePresent=%s, passwordPresent=%s]"
                .formatted(proxyType, host, port, username != null && !username.isBlank(),
                        password != null && !password.isBlank());
    }
}
