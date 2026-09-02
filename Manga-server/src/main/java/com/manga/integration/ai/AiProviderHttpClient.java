package com.manga.integration.ai;

import com.manga.common.constant.AiProviderConstants;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.Authenticator;
import java.net.PasswordAuthentication;
import java.net.Proxy;
import java.net.ProxySelector;
import java.net.SocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;

/** 统一执行 AI 服务 HTTP 请求，并按配置应用出站代理。 */
@Component
public class AiProviderHttpClient {

    /** 不使用代理时复用的 HTTP 客户端。 */
    private final HttpClient directHttpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(AiProviderConstants.CONNECTION_TEST_TIMEOUT_SECONDS))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    /** 发送 AI 服务 HTTP GET 请求。 */
    public HttpResponse<String> get(AiProviderHttpRequest request) throws IOException, InterruptedException {
        HttpRequest.Builder builder = HttpRequest.newBuilder(request.uri())
                .timeout(request.timeout())
                .GET();
        request.headers().forEach(builder::header);
        return httpClient(request.proxySettings()).send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    /** 按代理配置创建 HTTP 客户端。 */
    private HttpClient httpClient(AiProviderProxySettings proxySettings) {
        if (proxySettings == null) {
            return directHttpClient;
        }
        HttpClient.Builder builder = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(AiProviderConstants.CONNECTION_TEST_TIMEOUT_SECONDS))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .proxy(new FixedProxySelector(proxySettings.toJavaProxy()));
        if (proxySettings.hasCredentials()) {
            builder.authenticator(new Authenticator() {
                /** 提供代理服务器认证凭据。 */
                @Override
                protected PasswordAuthentication getPasswordAuthentication() {
                    if (getRequestorType() != RequestorType.PROXY) {
                        return null;
                    }
                    return new PasswordAuthentication(
                            proxySettings.username(),
                            proxySettings.passwordOrEmpty().toCharArray());
                }
            });
        }
        return builder.build();
    }

    /** 为 JDK HttpClient 固定选择当前 AI 配置指定的单个代理。 */
    private static final class FixedProxySelector extends ProxySelector {

        private final Proxy proxy;

        /** 创建固定代理选择器。 */
        private FixedProxySelector(Proxy proxy) {
            this.proxy = proxy;
        }

        /** 为目标地址返回固定代理。 */
        @Override
        public List<Proxy> select(URI uri) {
            return List.of(proxy);
        }

        /** 记录代理连接失败事件。 */
        @Override
        public void connectFailed(URI uri, SocketAddress address, IOException exception) {
            // JDK HttpClient reports the send failure to the caller; no separate handling is needed here.
        }
    }
}
