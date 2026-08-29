package com.manga.integration.wechat;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.manga.common.constant.ExceptionMessageConstants;
import com.manga.common.constant.WechatConstants;
import com.manga.config.properties.WechatOfficialAccountProperties;
import com.manga.repository.RedisKeyFactory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * 使用微信官方 HTTP API 获取调用凭据并创建临时二维码。
 */
@Component
@Slf4j
public class DefaultWechatOfficialAccountClient implements WechatOfficialAccountClient {

    private static final Duration ACCESS_TOKEN_REFRESH_MARGIN = Duration.ofMinutes(1);

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final WechatOfficialAccountProperties properties;
    private final StringRedisTemplate redisTemplate;
    private final RedisKeyFactory redisKeyFactory;

    public DefaultWechatOfficialAccountClient(
            RestClient.Builder restClientBuilder,
            ObjectMapper objectMapper,
            WechatOfficialAccountProperties properties,
            StringRedisTemplate redisTemplate,
            RedisKeyFactory redisKeyFactory) {
        this.restClient = restClientBuilder
                .requestFactory(new SimpleClientHttpRequestFactory())
                .baseUrl(properties.apiBaseUrl())
                .build();
        this.objectMapper = objectMapper;
        this.properties = properties;
        this.redisTemplate = redisTemplate;
        this.redisKeyFactory = redisKeyFactory;
    }

    /**
     * 创建带唯一字符串场景值的临时公众号二维码。
     */
    @Override
    public WechatQrCode createTemporaryQrCode(String sceneKey, Duration timeToLive) {
        validateConfiguration();
        try {
            String requestBody = serializeRequest(new QrCodeRequest(
                    timeToLive.toSeconds(),
                    WechatConstants.QR_ACTION_NAME,
                    new QrActionInfo(new QrScene(sceneKey))
            ));
            QrCodeApiResponse response = restClient.post()
                    .uri(uriBuilder -> uriBuilder
                            .path(WechatConstants.QR_CODE_CREATE_PATH)
                            .queryParam(WechatConstants.ACCESS_TOKEN_PARAMETER, accessToken())
                            .build())
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .contentLength(requestBody.getBytes(StandardCharsets.UTF_8).length)
                    .body(requestBody)
                    .retrieve()
                    .body(QrCodeApiResponse.class);
            if (response == null || !StringUtils.hasText(response.ticket())) {
                throw apiError(ExceptionMessageConstants.WECHAT_CREATE_QR_CODE_OPERATION,
                        response == null ? null : response.errorCode(),
                        response == null ? null : response.errorMessage());
            }
            String qrCodeUrl = properties.qrCodeBaseUrl()
                    + URLEncoder.encode(response.ticket(), StandardCharsets.UTF_8);
            log.debug("WeChat temporary QR code created expiresInSeconds={}", response.expiresIn());
            return new WechatQrCode(response.ticket(), qrCodeUrl, response.expiresIn());
        } catch (RestClientException exception) {
            throw new WechatApiException(ExceptionMessageConstants.WECHAT_QR_CODE_CREATION_FAILED, exception);
        }
    }

    private synchronized String accessToken() {
        try {
            String cacheKey = redisKeyFactory.wechatAccessToken(properties.appId());
            String cachedAccessToken = redisTemplate.opsForValue().get(cacheKey);
            if (StringUtils.hasText(cachedAccessToken)) {
                log.trace("WeChat access token cache hit");
                return cachedAccessToken;
            }
            AccessTokenApiResponse response = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path(WechatConstants.ACCESS_TOKEN_PATH)
                            .queryParam(WechatConstants.GRANT_TYPE_PARAMETER, WechatConstants.GRANT_TYPE)
                            .queryParam(WechatConstants.APP_ID_PARAMETER, properties.appId())
                            .queryParam(WechatConstants.SECRET_PARAMETER, properties.secret())
                            .build())
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .body(AccessTokenApiResponse.class);
            if (response == null || !StringUtils.hasText(response.accessToken())) {
                throw apiError(ExceptionMessageConstants.WECHAT_OBTAIN_ACCESS_TOKEN_OPERATION,
                        response == null ? null : response.errorCode(),
                        response == null ? null : response.errorMessage());
            }
            Duration effectiveTtl = Duration.ofSeconds(response.expiresIn()).minus(ACCESS_TOKEN_REFRESH_MARGIN);
            if (effectiveTtl.isZero() || effectiveTtl.isNegative()) {
                throw new WechatApiException(ExceptionMessageConstants.WECHAT_ACCESS_TOKEN_EXPIRATION_TOO_SHORT);
            }
            redisTemplate.opsForValue().set(cacheKey, response.accessToken(), effectiveTtl);
            log.info("WeChat access token refreshed ttlSeconds={}", effectiveTtl.toSeconds());
            return response.accessToken();
        } catch (RestClientException | DataAccessException exception) {
            throw new WechatApiException(ExceptionMessageConstants.WECHAT_ACCESS_TOKEN_OBTAIN_FAILED, exception);
        }
    }

    private void validateConfiguration() {
        if (!StringUtils.hasText(properties.appId())
                || !StringUtils.hasText(properties.secret())
                || !StringUtils.hasText(properties.apiBaseUrl())
                || !StringUtils.hasText(properties.qrCodeBaseUrl())) {
            throw new WechatApiException(ExceptionMessageConstants.WECHAT_CONFIGURATION_INCOMPLETE);
        }
    }

    private WechatApiException apiError(String operation, Integer errorCode, String errorMessage) {
        return new WechatApiException(ExceptionMessageConstants.WECHAT_API_OPERATION_FAILED_TEMPLATE.formatted(
                operation,
                errorCode,
                errorMessage
        ));
    }

    /** Serializes WeChat request payloads before sending them over the public API. */
    private String serializeRequest(Object request) {
        try {
            return objectMapper.writeValueAsString(request);
        } catch (JsonProcessingException exception) {
            throw new WechatApiException(ExceptionMessageConstants.WECHAT_REQUEST_SERIALIZATION_FAILED, exception);
        }
    }

    /** 描述稳定版 access_token 响应。 */
    private record AccessTokenApiResponse(
            @JsonProperty("access_token") String accessToken,
            @JsonProperty("expires_in") long expiresIn,
            @JsonProperty("errcode") Integer errorCode,
            @JsonProperty("errmsg") String errorMessage
    ) {
    }

    /** 描述临时二维码创建请求。 */
    private record QrCodeRequest(
            @JsonProperty("expire_seconds") long expiresIn,
            @JsonProperty("action_name") String actionName,
            @JsonProperty("action_info") QrActionInfo actionInfo
    ) {
    }

    /** 包装二维码场景参数。 */
    private record QrActionInfo(QrScene scene) {
    }

    /** 保存二维码字符串场景值。 */
    private record QrScene(@JsonProperty("scene_str") String sceneKey) {
    }

    /** 描述临时二维码创建响应。 */
    private record QrCodeApiResponse(
            String ticket,
            @JsonProperty("expire_seconds") long expiresIn,
            String url,
            @JsonProperty("errcode") Integer errorCode,
            @JsonProperty("errmsg") String errorMessage
    ) {
    }
}
