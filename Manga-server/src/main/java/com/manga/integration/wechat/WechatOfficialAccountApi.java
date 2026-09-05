package com.manga.integration.wechat;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.manga.common.constant.ExternalHttpEndpointConstants;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

/**
 * 微信公众号官方 HTTP API 的声明式调用契约。
 */
@HttpExchange(accept = MediaType.APPLICATION_JSON_VALUE)
public interface WechatOfficialAccountApi {

    /** 获取微信公众号 access_token。 */
    @GetExchange(ExternalHttpEndpointConstants.WECHAT_ACCESS_TOKEN_PATH)
    ResponseEntity<String> accessToken(
            @RequestParam("grant_type") String grantType,
            @RequestParam("appid") String appId,
            @RequestParam("secret") String secret);

    /** 创建微信公众号临时二维码。 */
    @PostExchange(value = ExternalHttpEndpointConstants.WECHAT_QR_CODE_CREATE_PATH,
            contentType = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<String> createTemporaryQrCode(
            @RequestParam("access_token") String accessToken,
            @RequestBody QrCodeRequest request);

    /** 描述临时二维码创建请求。 */
    record QrCodeRequest(
            /** 临时二维码有效期，单位为秒。 */
            @JsonProperty("expire_seconds") long expiresIn,
            /** 微信公众号二维码动作名称。 */
            @JsonProperty("action_name") String actionName,
            /** 微信公众号二维码动作参数。 */
            @JsonProperty("action_info") QrActionInfo actionInfo
    ) {
    }

    /** 包装二维码场景参数。 */
    record QrActionInfo(
            /** 微信公众号二维码场景参数。 */
            QrScene scene
    ) {
    }

    /** 保存二维码字符串场景值。 */
    record QrScene(
            /** 微信公众号二维码场景值。 */
            @JsonProperty("scene_str") String sceneKey
    ) {
    }
}
