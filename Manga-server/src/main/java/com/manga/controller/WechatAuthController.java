package com.manga.controller;

import com.manga.common.api.ApiResponse;
import com.manga.common.constant.WechatConstants;
import com.manga.dto.WechatQrLoginResponse;
import com.manga.dto.WechatQrLoginStatusResponse;
import com.manga.service.WechatAuthService;
import com.manga.service.WechatCallbackSignatureService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 提供微信公众号扫码登录与微信服务器事件回调接口。
 */
@RestController
@RequestMapping("/api/v1/auth/wechat")
@Slf4j
@RequiredArgsConstructor
public class WechatAuthController {

    private final WechatAuthService wechatAuthService;
    private final WechatCallbackSignatureService signatureService;

    /**
     * 创建用于关注或扫描公众号的临时登录二维码。
     */
    @PostMapping("/qr")
    public ApiResponse<WechatQrLoginResponse> createQrLogin() {
        log.info("[WechatAuthController#createQrLogin] request");
        WechatQrLoginResponse result = wechatAuthService.createQrLogin();
        log.info(
                "[WechatAuthController#createQrLogin] response expiresInSeconds={} pollIntervalSeconds={}",
                result.expiresIn(),
                result.pollInterval()
        );
        return ApiResponse.success(result);
    }

    /**
     * 轮询扫码登录结果，并在确认后返回一次 Manga 登录令牌。
     */
    @GetMapping("/qr/{loginToken}")
    public ApiResponse<WechatQrLoginStatusResponse> queryQrLogin(@PathVariable String loginToken) {
        log.info("[WechatAuthController#queryQrLogin] request tokenLength={}", loginToken.length());
        WechatQrLoginStatusResponse result = wechatAuthService.queryQrLogin(loginToken);
        log.info(
                "[WechatAuthController#queryQrLogin] response status={} expiresInSeconds={} authenticated={}",
                result.status(),
                result.expiresIn(),
                result.authentication() != null
        );
        return ApiResponse.success(result);
    }

    /**
     * 响应微信服务器首次配置时发起的签名校验。
     */
    @GetMapping(value = "/callback", produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<String> verifyCallback(
            @RequestParam(WechatConstants.SIGNATURE_PARAMETER) String signature,
            @RequestParam(WechatConstants.TIMESTAMP_PARAMETER) String timestamp,
            @RequestParam(WechatConstants.NONCE_PARAMETER) String nonce,
            @RequestParam(WechatConstants.ECHO_STRING_PARAMETER) String echoString) {
        log.info(
                "[WechatAuthController#verifyCallback] request timestamp={} echoLength={}",
                timestamp,
                echoString.length()
        );
        if (!signatureService.isValid(signature, timestamp, nonce)) {
            log.info(
                    "[WechatAuthController#verifyCallback] response httpStatus={}",
                    HttpStatus.FORBIDDEN.value()
            );
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("");
        }
        log.info("[WechatAuthController#verifyCallback] response httpStatus={}", HttpStatus.OK.value());
        return ResponseEntity.ok(echoString);
    }

    /**
     * 接收关注和扫码事件，绑定微信身份并确认登录会话。
     */
    @PostMapping(
            value = "/callback",
            consumes = {MediaType.TEXT_XML_VALUE, MediaType.APPLICATION_XML_VALUE},
            produces = MediaType.TEXT_PLAIN_VALUE
    )
    public ResponseEntity<String> receiveCallback(
            @RequestParam(WechatConstants.SIGNATURE_PARAMETER) String signature,
            @RequestParam(WechatConstants.TIMESTAMP_PARAMETER) String timestamp,
            @RequestParam(WechatConstants.NONCE_PARAMETER) String nonce,
            @RequestBody String callbackXml) {
        log.info(
                "[WechatAuthController#receiveCallback] request timestamp={} payloadLength={}",
                timestamp,
                callbackXml.length()
        );
        if (!signatureService.isValid(signature, timestamp, nonce)) {
            log.info(
                    "[WechatAuthController#receiveCallback] response httpStatus={}",
                    HttpStatus.FORBIDDEN.value()
            );
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("");
        }
        wechatAuthService.handleCallback(callbackXml);
        log.info("[WechatAuthController#receiveCallback] response httpStatus={}", HttpStatus.OK.value());
        return ResponseEntity.ok(WechatConstants.CALLBACK_SUCCESS_RESPONSE);
    }
}
