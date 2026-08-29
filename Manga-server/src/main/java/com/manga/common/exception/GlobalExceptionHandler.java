package com.manga.common.exception;

import com.manga.common.api.ApiResponse;
import com.manga.common.enums.CommonResponseCode;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 将控制器抛出的异常转换为统一接口响应。
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * 返回业务异常指定的响应码和 HTTP 状态。
     */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusinessException(BusinessException exception) {
        log.debug("Business request rejected code={} status={}",
                exception.getResponseCode().code(), exception.getStatus().value());
        return ResponseEntity.status(exception.getStatus())
                .body(ApiResponse.failure(exception.getResponseCode(), exception.getMessage()));
    }

    /**
     * 汇总请求对象的字段校验错误。
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Map<String, String>>> handleValidationException(
            MethodArgumentNotValidException exception) {
        Map<String, String> errors = new LinkedHashMap<>();
        for (FieldError error : exception.getBindingResult().getFieldErrors()) {
            errors.putIfAbsent(error.getField(), error.getDefaultMessage());
        }
        log.warn("Request validation failed fields={}", errors.keySet());
        ApiResponse<Map<String, String>> response = ApiResponse.failure(CommonResponseCode.VALIDATION_ERROR, errors);
        return ResponseEntity.badRequest().body(response);
    }

    /**
     * 处理路径参数和请求参数的约束校验错误。
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleConstraintViolation(ConstraintViolationException exception) {
        log.warn("Constraint validation failed count={}", exception.getConstraintViolations().size());
        return ResponseEntity.badRequest()
                .body(ApiResponse.failure(CommonResponseCode.VALIDATION_ERROR, exception.getMessage()));
    }

    /**
     * 将方法级鉴权拒绝统一转换为禁止访问响应。
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> handleAccessDenied(AccessDeniedException exception) {
        log.warn("Access denied by method security");
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ApiResponse.failure(CommonResponseCode.FORBIDDEN));
    }

    /**
     * 记录未预期异常并隐藏内部实现细节。
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnexpectedException(Exception exception) {
        log.error("Unhandled server exception", exception);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.failure(CommonResponseCode.INTERNAL_ERROR));
    }
}
