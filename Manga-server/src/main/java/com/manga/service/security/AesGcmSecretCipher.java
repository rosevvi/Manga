package com.manga.service.security;

import com.manga.common.constant.AiSecretConstants;
import com.manga.common.constant.ExceptionMessageConstants;
import com.manga.common.enums.AiProviderConfigResponseCode;
import com.manga.common.exception.BusinessException;
import com.manga.config.properties.AiSecretProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/** 使用带随机 IV 和认证标签的 AES-GCM 加密 AI 凭据。 */
@Component
@RequiredArgsConstructor
public class AesGcmSecretCipher implements SecretCipher {

    /** 生成 AES-GCM 随机 IV 的安全随机数源。 */
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final AiSecretProperties secretProperties;

    /** 使用 AES-GCM 加密敏感文本。 */
    @Override
    public String encrypt(String plaintext) {
        if (plaintext == null) {
            return null;
        }
        try {
            byte[] iv = new byte[AiSecretConstants.GCM_IV_BYTES];
            SECURE_RANDOM.nextBytes(iv);
            Cipher cipher = Cipher.getInstance(AiSecretConstants.AES_TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, encryptionKey(),
                    new GCMParameterSpec(AiSecretConstants.GCM_TAG_BITS, iv));
            byte[] encrypted = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            byte[] payload = ByteBuffer.allocate(iv.length + encrypted.length).put(iv).put(encrypted).array();
            return AiSecretConstants.CIPHERTEXT_VERSION_PREFIX + Base64.getEncoder().encodeToString(payload);
        } catch (BusinessException exception) {
            throw exception;
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException(ExceptionMessageConstants.AI_SECRET_ENCRYPTION_FAILED, exception);
        }
    }

    /** 解密 AES-GCM 密文。 */
    @Override
    public String decrypt(String ciphertext) {
        if (ciphertext == null) {
            return null;
        }
        if (!ciphertext.startsWith(AiSecretConstants.CIPHERTEXT_VERSION_PREFIX)) {
            throw new BusinessException(
                    AiProviderConfigResponseCode.SECRET_DECRYPTION_FAILED, HttpStatus.INTERNAL_SERVER_ERROR);
        }
        try {
            byte[] payload = Base64.getDecoder().decode(
                    ciphertext.substring(AiSecretConstants.CIPHERTEXT_VERSION_PREFIX.length()));
            ByteBuffer buffer = ByteBuffer.wrap(payload);
            byte[] iv = new byte[AiSecretConstants.GCM_IV_BYTES];
            buffer.get(iv);
            byte[] encrypted = new byte[buffer.remaining()];
            buffer.get(encrypted);
            Cipher cipher = Cipher.getInstance(AiSecretConstants.AES_TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, encryptionKey(),
                    new GCMParameterSpec(AiSecretConstants.GCM_TAG_BITS, iv));
            return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
        } catch (BusinessException exception) {
            throw exception;
        } catch (RuntimeException | GeneralSecurityException exception) {
            throw new BusinessException(
                    AiProviderConfigResponseCode.SECRET_DECRYPTION_FAILED,
                    AiProviderConfigResponseCode.SECRET_DECRYPTION_FAILED.message(),
                    HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    /** 解析并校验 AES 加密密钥。 */
    private SecretKeySpec encryptionKey() throws GeneralSecurityException {
        String configuredKey = secretProperties.encryptionKey();
        if (configuredKey == null || configuredKey.isBlank()) {
            throw new BusinessException(
                    AiProviderConfigResponseCode.ENCRYPTION_KEY_MISSING,
                    HttpStatus.SERVICE_UNAVAILABLE);
        }
        MessageDigest digest = MessageDigest.getInstance(AiSecretConstants.KEY_DIGEST_ALGORITHM);
        return new SecretKeySpec(
                digest.digest(configuredKey.getBytes(StandardCharsets.UTF_8)),
                AiSecretConstants.AES_ALGORITHM);
    }
}
