package com.manga.common.api;

/**
 * 定义返回给客户端的标准响应码契约。
 */
public interface ResponseCode {

    /**
     * 返回稳定的业务响应码。
     */
    String code();

    /**
     * 返回默认响应说明。
     */
    String message();
}
