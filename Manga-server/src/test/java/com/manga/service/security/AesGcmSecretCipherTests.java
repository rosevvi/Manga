package com.manga.service.security;

import com.manga.common.enums.AiProviderConfigResponseCode;
import com.manga.common.exception.BusinessException;
import com.manga.config.properties.AiSecretProperties;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 验证 AI 凭据加密的可逆性、随机性和缺失配置处理。 */
class AesGcmSecretCipherTests {

    private static final String ENCRYPTION_KEY = "manga-test-ai-encryption-key-with-enough-entropy";
    private static final String API_KEY = "sk-test-sensitive-value";

    /** 验证随机 IV 加密结果不同且均可正确解密。 */
    @Test
    void shouldEncryptWithRandomIvAndDecrypt() {
        SecretCipher cipher = new AesGcmSecretCipher(new AiSecretProperties(ENCRYPTION_KEY));

        String firstCiphertext = cipher.encrypt(API_KEY);
        String secondCiphertext = cipher.encrypt(API_KEY);

        assertThat(firstCiphertext).isNotEqualTo(secondCiphertext);
        assertThat(firstCiphertext).doesNotContain(API_KEY);
        assertThat(cipher.decrypt(firstCiphertext)).isEqualTo(API_KEY);
        assertThat(cipher.decrypt(secondCiphertext)).isEqualTo(API_KEY);
    }

    /** 验证缺少服务端密钥时拒绝执行加密。 */
    @Test
    void shouldRejectEncryptionWhenServerKeyIsMissing() {
        SecretCipher cipher = new AesGcmSecretCipher(new AiSecretProperties(""));

        assertThatThrownBy(() -> cipher.encrypt(API_KEY))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getResponseCode())
                                .isEqualTo(AiProviderConfigResponseCode.ENCRYPTION_KEY_MISSING));
    }
}
