package com.manga.common.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.manga.common.api.ApiResponse;
import com.manga.common.api.ResponseCode;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/** 将认证和授权失败写为统一 API 响应。 */
@Component
@RequiredArgsConstructor
public class SecurityErrorResponseWriter {

    private final ObjectMapper objectMapper;

    /** 按项目统一响应结构写入认证或授权错误。 */
    public void write(HttpServletResponse response, int status, ResponseCode responseCode) throws IOException {
        response.setStatus(status);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getWriter(), ApiResponse.failure(responseCode));
    }
}
