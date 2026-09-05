package com.manga.integration.wechat;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.manga.common.constant.ExceptionMessageConstants;
import com.manga.common.constant.WechatConstants;
import com.manga.config.properties.WechatOfficialAccountProperties;
import com.manga.integration.http.ExternalHttpClientFactory;
import com.manga.repository.RedisKeyFactory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpHeaders;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * 使用微信公众号官方 HTTP API 获取调用凭据并创建临时二维码。
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class DefaultWechatOfficialAccountClient implements WechatOfficialAccountClient {

    /** 微信 access_token 到期前的主动刷新余量。 */
    private static final Duration ACCESS_TOKEN_REFRESH_MARGIN = Duration.ofMinutes(1);

    /** 外部 HTTP 客户端工厂。 */
    private final ExternalHttpClientFactory externalHttpClientFactory;
    /** JSON 序列化和反序列化组件。 */
    private final ObjectMapper objectMapper;
    /** 微信公众号配置。 */
    private final WechatOfficialAccountProperties properties;
    /** Redis 字符串模板。 */
    private final StringRedisTemplate redisTemplate;
    /** Redis Key 生成器。 */
    private final RedisKeyFactory redisKeyFactory;

    /** 创建带唯一字符串场景值的临时公众号二维码。 */
    @Override
    public WechatQrCode createTemporaryQrCode(String sceneKey, Duration timeToLive) {
        validateConfiguration();
        try {
            WechatOfficialAccountApi api = wechatOfficialAccountApi();
            ResponseEntity<String> response;
            try {
                response = api.createTemporaryQrCode(
                        accessToken(),
                        new WechatOfficialAccountApi.QrCodeRequest(
                                timeToLive.toSeconds(),
                                WechatConstants.QR_ACTION_NAME,
                                new WechatOfficialAccountApi.QrActionInfo(new WechatOfficialAccountApi.QrScene(sceneKey))));
            } catch (RestClientResponseException exception) {
                response = ResponseEntity.status(exception.getStatusCode())
                        .headers(exception.getResponseHeaders())
                        .body(exception.getResponseBodyAsString());
            }
            QrCodeApiResponse body = parseResponse(response, QrCodeApiResponse.class);
            if (body == null || !StringUtils.hasText(body.ticket())) {
                throw apiError(ExceptionMessageConstants.WECHAT_CREATE_QR_CODE_OPERATION,
                        body == null ? null : body.errorCode(),
                        body == null ? null : body.errorMessage());
            }
            String qrCodeUrl = properties.qrCodeBaseUrl() + URLEncoder.encode(body.ticket(), StandardCharsets.UTF_8);
            log.debug("WeChat temporary QR code created expiresInSeconds={}", body.expiresIn());
            return new WechatQrCode(body.ticket(), qrCodeUrl, body.expiresIn());
        } catch (WechatApiException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new WechatApiException(ExceptionMessageConstants.WECHAT_QR_CODE_CREATION_FAILED, exception);
        }
    }

    /** 获取并缓存微信公众号 access_token。 */
    private synchronized String accessToken() {
        try {
            String cacheKey = redisKeyFactory.wechatAccessToken(properties.appId());
            String cachedAccessToken = redisTemplate.opsForValue().get(cacheKey);
            if (StringUtils.hasText(cachedAccessToken)) {
                log.trace("WeChat access token cache hit");
                return cachedAccessToken;
            }
            WechatOfficialAccountApi api = wechatOfficialAccountApi();
            ResponseEntity<String> response;
            try {
                response = api.accessToken(
                        WechatConstants.GRANT_TYPE,
                        properties.appId(),
                        properties.secret());
            } catch (RestClientResponseException exception) {
                response = ResponseEntity.status(exception.getStatusCode())
                        .headers(exception.getResponseHeaders() == null ? new HttpHeaders() : exception.getResponseHeaders())
                        .body(exception.getResponseBodyAsString());
            }
            AccessTokenApiResponse body = parseResponse(response, AccessTokenApiResponse.class);
            if (body == null || !StringUtils.hasText(body.accessToken())) {
                throw apiError(ExceptionMessageConstants.WECHAT_OBTAIN_ACCESS_TOKEN_OPERATION,
                        body == null ? null : body.errorCode(),
                        body == null ? null : body.errorMessage());
            }
            Duration effectiveTtl = Duration.ofSeconds(body.expiresIn()).minus(ACCESS_TOKEN_REFRESH_MARGIN);
            if (effectiveTtl.isZero() || effectiveTtl.isNegative()) {
                throw new WechatApiException(ExceptionMessageConstants.WECHAT_ACCESS_TOKEN_EXPIRATION_TOO_SHORT);
            }
            redisTemplate.opsForValue().set(cacheKey, body.accessToken(), effectiveTtl);
            log.info("WeChat access token refreshed ttlSeconds={}", effectiveTtl.toSeconds());
            return body.accessToken();
        } catch (WechatApiException exception) {
            throw exception;
        } catch (DataAccessException exception) {
            throw new WechatApiException(ExceptionMessageConstants.WECHAT_ACCESS_TOKEN_OBTAIN_FAILED, exception);
        } catch (RuntimeException exception) {
            throw new WechatApiException(ExceptionMessageConstants.WECHAT_ACCESS_TOKEN_OBTAIN_FAILED, exception);
        }
    }

    /** 获取微信公众号声明式客户端。 */
    private WechatOfficialAccountApi wechatOfficialAccountApi() {
        return externalHttpClientFactory.createRestService(
                WechatOfficialAccountApi.class,
                properties.apiBaseUrl(),
                null);
    }

    /** 校验微信公众号接口配置。 */
    private void validateConfiguration() {
        if (!StringUtils.hasText(properties.appId())
                || !StringUtils.hasText(properties.secret())
                || !StringUtils.hasText(properties.apiBaseUrl())
                || !StringUtils.hasText(properties.qrCodeBaseUrl())) {
            throw new WechatApiException(ExceptionMessageConstants.WECHAT_CONFIGURATION_INCOMPLETE);
        }
    }

    /** 构造微信公众号接口异常。 */
    private WechatApiException apiError(String operation, Integer errorCode, String errorMessage) {
        return new WechatApiException(ExceptionMessageConstants.WECHAT_API_OPERATION_FAILED_TEMPLATE.formatted(
                operation,
                errorCode,
                errorMessage
        ));
    }

    /** 解析响应正文。 */
    private <T> T parseResponse(ResponseEntity<String> response, Class<T> responseType) {
        if (response == null || !StringUtils.hasText(response.getBody())) {
            return null;
        }
        try {
            return objectMapper.readValue(response.getBody(), responseType);
        } catch (JsonProcessingException exception) {
            throw new WechatApiException(ExceptionMessageConstants.WECHAT_RESPONSE_DESERIALIZATION_FAILED, exception);
        }
    }

    /** 描述稳定版 access_token 响应。 */
    private record AccessTokenApiResponse(
            /** 微信公众号 access_token。 */
            @JsonProperty("access_token") String accessToken,
            /** access_token 有效期，单位为秒。 */
            @JsonProperty("expires_in") long expiresIn,
            /** 微信公众号接口错误码。 */
            @JsonProperty("errcode") Integer errorCode,
            /** 微信公众号接口错误描述。 */
            @JsonProperty("errmsg") String errorMessage
    ) {
    }

    /** 描述临时二维码创建响应。 */
    private record QrCodeApiResponse(
            /** 微信公众号二维码票据。 */
            String ticket,
            /** 临时二维码有效期，单位为秒。 */
            @JsonProperty("expire_seconds") long expiresIn,
            /** 微信公众号返回的二维码内容地址。 */
            String url,
            /** 微信公众号接口错误码。 */
            @JsonProperty("errcode") Integer errorCode,
            /** 微信公众号接口错误描述。 */
            @JsonProperty("errmsg") String errorMessage
    ) {
    }
}
