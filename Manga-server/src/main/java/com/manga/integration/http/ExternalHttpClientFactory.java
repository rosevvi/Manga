package com.manga.integration.http;

import com.manga.config.properties.ExternalHttpProperties;
import io.netty.channel.ChannelOption;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.http.client.BufferingClientHttpRequestFactory;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.support.RestClientAdapter;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.support.WebClientAdapter;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;
import reactor.netty.http.client.HttpClient;
import reactor.netty.transport.ProxyProvider;

import java.net.Authenticator;
import java.net.PasswordAuthentication;
import java.net.Proxy;
import java.net.ProxySelector;
import java.net.SocketAddress;
import java.net.URI;
import java.net.http.HttpClient.Redirect;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 统一创建外部 HTTP 的同步和流式声明式客户端。
 */
@Component
@EnableConfigurationProperties(ExternalHttpProperties.class)
public class ExternalHttpClientFactory {

    /** 同步客户端缓存。 */
    private final ConcurrentMap<ClientCacheKey, Object> restClientCache = new ConcurrentHashMap<>();
    /** 流式客户端缓存。 */
    private final ConcurrentMap<ClientCacheKey, Object> webClientCache = new ConcurrentHashMap<>();
    /** 外部 HTTP 基础配置。 */
    private final ExternalHttpProperties properties;

    /** 初始化外部 HTTP 客户端工厂。 */
    public ExternalHttpClientFactory(ExternalHttpProperties properties) {
        this.properties = properties;
    }

    /** 创建同步 HTTP 服务接口代理。 */
    public <T> T createRestService(Class<T> serviceType, String baseUrl, ExternalProxySettings proxySettings) {
        String normalizedBaseUrl = normalizeBaseUrl(baseUrl);
        ClientCacheKey cacheKey = ClientCacheKey.rest(serviceType, normalizedBaseUrl, proxySettings);
        return serviceType.cast(restClientCache.computeIfAbsent(cacheKey,
                key -> createRestServiceInternal(serviceType, normalizedBaseUrl, proxySettings)));
    }

    /** 创建流式 HTTP 服务接口代理。 */
    public <T> T createWebService(Class<T> serviceType, String baseUrl, ExternalProxySettings proxySettings) {
        String normalizedBaseUrl = normalizeBaseUrl(baseUrl);
        ClientCacheKey cacheKey = ClientCacheKey.web(serviceType, normalizedBaseUrl, proxySettings);
        return serviceType.cast(webClientCache.computeIfAbsent(cacheKey,
                key -> createWebServiceInternal(serviceType, normalizedBaseUrl, proxySettings)));
    }

    /** 创建同步服务接口代理。 */
    private <T> T createRestServiceInternal(Class<T> serviceType, String baseUrl, ExternalProxySettings proxySettings) {
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(createJdkClient(proxySettings));
        requestFactory.setReadTimeout(normalizeRequestTimeout());
        RestClient restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(new BufferingClientHttpRequestFactory(requestFactory))
                .build();
        HttpServiceProxyFactory factory = HttpServiceProxyFactory.builderFor(RestClientAdapter.create(restClient))
                .build();
        return factory.createClient(serviceType);
    }

    /** 创建流式服务接口代理。 */
    private <T> T createWebServiceInternal(Class<T> serviceType, String baseUrl, ExternalProxySettings proxySettings) {
        WebClient webClient = WebClient.builder()
                .baseUrl(baseUrl)
                .clientConnector(new ReactorClientHttpConnector(createReactorClient(proxySettings)))
                .build();
        HttpServiceProxyFactory factory = HttpServiceProxyFactory.builderFor(WebClientAdapter.create(webClient))
                .build();
        return factory.createClient(serviceType);
    }

    /** 创建 JDK HTTP 客户端。 */
    private java.net.http.HttpClient createJdkClient(ExternalProxySettings proxySettings) {
        java.net.http.HttpClient.Builder builder = java.net.http.HttpClient.newBuilder()
                .connectTimeout(normalizeConnectTimeout())
                .followRedirects(Redirect.NORMAL);
        if (proxySettings != null && proxySettings.enabled()) {
            builder.proxy(createProxySelector(proxySettings));
            if (proxySettings.hasCredentials()) {
                builder.authenticator(createAuthenticator(proxySettings));
            }
        }
        return builder.build();
    }

    /** 创建 WebClient 使用的 Reactor 客户端。 */
    private HttpClient createReactorClient(ExternalProxySettings proxySettings) {
        HttpClient client = HttpClient.create()
                .followRedirect(true)
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, Math.toIntExact(normalizeConnectTimeout().toMillis()))
                .responseTimeout(normalizeRequestTimeout());
        if (proxySettings != null && proxySettings.enabled()) {
            client = client.proxy(proxy -> {
                ProxyProvider.Builder proxyBuilder = proxy.type(proxySettings.proxyType() == ExternalProxyType.SOCKS5
                        ? ProxyProvider.Proxy.SOCKS5
                        : ProxyProvider.Proxy.HTTP)
                        .host(proxySettings.host())
                        .port(proxySettings.port());
                if (proxySettings.hasCredentials()) {
                    proxyBuilder.username(proxySettings.username());
                    proxyBuilder.password(ignored -> proxySettings.password());
                }
            });
        }
        return client;
    }

    /** 创建 JDK 代理选择器。 */
    private ProxySelector createProxySelector(ExternalProxySettings proxySettings) {
        Proxy.Type proxyType = proxySettings.proxyType() == ExternalProxyType.SOCKS5
                ? Proxy.Type.SOCKS
                : Proxy.Type.HTTP;
        Proxy proxy = new Proxy(proxyType, new java.net.InetSocketAddress(proxySettings.host(), proxySettings.port()));
        return new ProxySelector() {
            /** 为目标地址返回固定代理。 */
            @Override
            public List<Proxy> select(URI uri) {
                return List.of(proxy);
            }

            /** 记录代理连接失败事件。 */
            @Override
            public void connectFailed(URI uri, SocketAddress address, java.io.IOException exception) {
                // 由上层调用方统一处理连接失败。
            }
        };
    }

    /** 创建代理认证器。 */
    private Authenticator createAuthenticator(ExternalProxySettings proxySettings) {
        return new Authenticator() {
            /** 提供代理服务器认证凭据。 */
            @Override
            protected PasswordAuthentication getPasswordAuthentication() {
                if (getRequestorType() != RequestorType.PROXY) {
                    return null;
                }
                return new PasswordAuthentication(proxySettings.username(), proxySettings.password().toCharArray());
            }
        };
    }

    /** 规范化基础地址。 */
    private String normalizeBaseUrl(String baseUrl) {
        String normalized = Objects.requireNonNull(baseUrl, "baseUrl").trim().replaceAll("/+$", "");
        if (normalized.isBlank()) {
            throw new IllegalArgumentException("baseUrl");
        }
        return normalized;
    }

    /** 规范化连接超时时间。 */
    private Duration normalizeConnectTimeout() {
        Duration connectTimeout = properties.connectTimeout();
        return connectTimeout == null ? Duration.ofSeconds(10) : connectTimeout;
    }

    /** 规范化请求超时时间。 */
    private Duration normalizeRequestTimeout() {
        Duration requestTimeout = properties.requestTimeout();
        return requestTimeout == null ? Duration.ofSeconds(30) : requestTimeout;
    }

    /** 缓存键。 */
    private record ClientCacheKey(Class<?> serviceType, String baseUrl, ExternalProxySettings proxySettings,
                                  Kind kind) {

        /** 创建 RestClient 缓存键。 */
        private static ClientCacheKey rest(Class<?> serviceType, String baseUrl, ExternalProxySettings proxySettings) {
            return new ClientCacheKey(serviceType, baseUrl, proxySettings, Kind.REST);
        }

        /** 创建 WebClient 缓存键。 */
        private static ClientCacheKey web(Class<?> serviceType, String baseUrl, ExternalProxySettings proxySettings) {
            return new ClientCacheKey(serviceType, baseUrl, proxySettings, Kind.WEB);
        }
    }

    /** 客户端类型。 */
    private enum Kind {
        /** 同步客户端。 */
        REST,
        /** 流式客户端。 */
        WEB
    }
}
