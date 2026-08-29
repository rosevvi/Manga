package com.manga.common.constant;

/** 统一维护 AI 凭据加密格式与密钥摘要参数。 */
public final class AiSecretConstants {

    public static final String CIPHERTEXT_VERSION_PREFIX = "v1:";
    public static final String AES_TRANSFORMATION = "AES/GCM/NoPadding";
    public static final String AES_ALGORITHM = "AES";
    public static final String KEY_DIGEST_ALGORITHM = "SHA-256";
    public static final int GCM_IV_BYTES = 12;
    public static final int GCM_TAG_BITS = 128;
    public static final int API_KEY_HINT_VISIBLE_CHARACTERS = 4;
    public static final String API_KEY_HINT_MASK = "••••";

    private AiSecretConstants() {
    }
}
