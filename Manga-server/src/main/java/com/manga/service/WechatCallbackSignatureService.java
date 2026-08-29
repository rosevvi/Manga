package com.manga.service;

import com.manga.common.constant.ExceptionMessageConstants;
import com.manga.common.constant.WechatConstants;
import com.manga.config.properties.WechatOfficialAccountProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HexFormat;

/**
 * 校验微信公众号服务器回调请求签名。
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class WechatCallbackSignatureService {

    private final WechatOfficialAccountProperties properties;

    /**
     * 按微信协议对 Token、时间戳和随机数进行 SHA-1 校验。
     */
    public boolean isValid(String signature, String timestamp, String nonce) {
        if (!StringUtils.hasText(properties.token())
                || !StringUtils.hasText(signature)
                || !StringUtils.hasText(timestamp)
                || !StringUtils.hasText(nonce)) {
            log.warn("Invalid WeChat callback signature rejected");
            return false;
        }
        String[] values = {properties.token(), timestamp, nonce};
        Arrays.sort(values);
        try {
            MessageDigest digest = MessageDigest.getInstance(WechatConstants.SHA_1_ALGORITHM);
            String calculated = HexFormat.of().formatHex(
                    digest.digest(String.join("", values).getBytes(StandardCharsets.UTF_8))
            );
            boolean valid = MessageDigest.isEqual(
                    calculated.getBytes(StandardCharsets.US_ASCII),
                    signature.getBytes(StandardCharsets.US_ASCII)
            );
            if (!valid) {
                log.warn("Invalid WeChat callback signature rejected");
            }
            return valid;
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(
                    ExceptionMessageConstants.ALGORITHM_UNAVAILABLE_TEMPLATE
                            .formatted(WechatConstants.SHA_1_ALGORITHM),
                    exception
            );
        }
    }
}
