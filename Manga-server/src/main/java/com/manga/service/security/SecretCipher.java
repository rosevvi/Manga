package com.manga.service.security;

/** 定义敏感配置的可替换加密与解密策略。 */
public interface SecretCipher {

    String encrypt(String plaintext);

    String decrypt(String ciphertext);
}
