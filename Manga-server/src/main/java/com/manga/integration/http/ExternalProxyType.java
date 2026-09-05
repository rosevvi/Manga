package com.manga.integration.http;

/**
 * 定义外部 HTTP 客户端使用的代理类型。
 */
public enum ExternalProxyType {
    /** 不使用代理。 */
    NONE,
    /** 使用 HTTP 代理。 */
    HTTP,
    /** 使用 SOCKS5 代理。 */
    SOCKS5
}
