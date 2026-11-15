package com.manga.integration.ai;

import com.manga.common.enums.AiProviderType;

/** 定义 Agnes 图片、视频和连接检测共同使用的官方网关。 */
public final class AgnesEndpoint {
    /** 官方根地址，用于视频提交及查询。 */
    public static final String BASE_URL = AiProviderType.AGNES.defaultBaseUrl();
    /** OpenAI 兼容的版本化基础地址。 */
    public static final String VERSIONED_URL = BASE_URL + "/v1";

    private AgnesEndpoint() { }

    /** 允许两种官方写法，避免把 Agnes 密钥发往其他端点。 */
    public static boolean supports(String address) {
        return BASE_URL.equals(address) || VERSIONED_URL.equals(address);
    }
}
