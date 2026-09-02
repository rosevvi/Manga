package com.manga.integration.ai;

import java.net.URI;
import java.time.Duration;
import java.util.Map;

/** 承载一次 AI 服务端 HTTP 请求所需的 URL、Header、超时和代理设置。 */
public record AiProviderHttpRequest(
        /** HTTP 请求目标地址。 */
        URI uri,
        /** HTTP 请求头集合。 */
        Map<String, String> headers,
        /** HTTP 请求超时时长。 */
        Duration timeout,
        /** 当前请求的代理设置。 */
        AiProviderProxySettings proxySettings
) {

    /** 创建 AI 服务 GET 请求参数。 */
    public static AiProviderHttpRequest get(
            URI uri,
            Map<String, String> headers,
            Duration timeout,
            AiProviderProxySettings proxySettings) {
        return new AiProviderHttpRequest(uri, Map.copyOf(headers), timeout, proxySettings);
    }

    /** 返回不包含敏感请求头的日志字符串。 */
    @Override
    public String toString() {
        return "AiProviderHttpRequest[host=%s, path=%s, headerNames=%s, timeout=%s, proxyEnabled=%s]"
                .formatted(uri.getHost(), uri.getPath(), headers.keySet(), timeout, proxySettings != null);
    }
}
