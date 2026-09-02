package com.manga.common.exception;

import com.manga.common.api.ResponseCode;
import org.springframework.http.HttpStatus;

/**
 * 表示可转换为标准接口响应的业务异常。
 */
public class BusinessException extends RuntimeException {

    /** 对外返回的稳定业务响应码。 */
    private final ResponseCode responseCode;
    /** 接口返回使用的 HTTP 状态。 */
    private final HttpStatus status;

    /** 构造携带业务响应信息的异常。 */
    public BusinessException(ResponseCode responseCode) {
        this(responseCode, responseCode.message(), HttpStatus.BAD_REQUEST);
    }

    /** 构造携带业务响应信息的异常。 */
    public BusinessException(ResponseCode responseCode, HttpStatus status) {
        this(responseCode, responseCode.message(), status);
    }

    /** 构造携带业务响应信息的异常。 */
    public BusinessException(ResponseCode responseCode, String message, HttpStatus status) {
        super(message);
        this.responseCode = responseCode;
        this.status = status;
    }

    /** 返回业务响应码。 */
    public ResponseCode getResponseCode() {
        return responseCode;
    }

    /** 返回对应的 HTTP 状态。 */
    public HttpStatus getStatus() {
        return status;
    }
}
