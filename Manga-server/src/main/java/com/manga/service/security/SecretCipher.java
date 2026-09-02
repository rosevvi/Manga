package com.manga.service.security;

/** 定义敏感配置的可替换加密与解密策略。 */
public interface SecretCipher {

    /** 加密敏感文本。 */
    String encrypt(String plaintext);

    /** 解密敏感文本。 */
    String decrypt(String ciphertext);
}
