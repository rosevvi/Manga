package com.manga.common.exception;

import com.manga.common.api.ResponseCode;
import org.springframework.http.HttpStatus;

/**
 * 表示可转换为标准接口响应的业务异常。
 */
public class BusinessException extends RuntimeException {

    private final ResponseCode responseCode;
    private final HttpStatus status;

    public BusinessException(ResponseCode responseCode) {
        this(responseCode, responseCode.message(), HttpStatus.BAD_REQUEST);
    }

    public BusinessException(ResponseCode responseCode, HttpStatus status) {
        this(responseCode, responseCode.message(), status);
    }

    public BusinessException(ResponseCode responseCode, String message, HttpStatus status) {
        super(message);
        this.responseCode = responseCode;
        this.status = status;
    }

    public ResponseCode getResponseCode() {
        return responseCode;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
