package com.manga.common.constant;

/** 统一维护 AI 凭据加密格式与密钥摘要参数。 */
public final class AiSecretConstants {

    /** AES-GCM 密文版本前缀。 */
    public static final String CIPHERTEXT_VERSION_PREFIX = "v1:";
    /** AES-GCM 加密算法转换名称。 */
    public static final String AES_TRANSFORMATION = "AES/GCM/NoPadding";
    /** AES 密钥算法名称。 */
    public static final String AES_ALGORITHM = "AES";
    /** 服务端密钥摘要算法名称。 */
    public static final String KEY_DIGEST_ALGORITHM = "SHA-256";
    /** AES-GCM 随机 IV 字节数。 */
    public static final int GCM_IV_BYTES = 12;
    /** AES-GCM 认证标签位数。 */
    public static final int GCM_TAG_BITS = 128;
    /** 密钥摘要首尾可见字符数。 */
    public static final int API_KEY_HINT_VISIBLE_CHARACTERS = 4;
    /** 密钥摘要中间掩码。 */
    public static final String API_KEY_HINT_MASK = "••••";

    /** 禁止实例化常量类。 */
    private AiSecretConstants() {
    }
}
