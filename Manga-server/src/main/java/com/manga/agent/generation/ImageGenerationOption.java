package com.manga.agent.generation;

import java.math.BigDecimal;
import java.util.List;
/** 生成入口需要的最小配置摘要，不返回 API Key 或代理密码。 */
public record ImageGenerationOption(
        /** 供应商路由。 */ GenerationProviderKey provider,
        /** 配置主键，Fake 为空。 */ Long configId,
        /** 配置展示名称。 */ String name,
        /** 允许模型。 */ String model,
        /** 预计单张费用；真实供应商未核价时为空。 */ BigDecimal estimatedCost,
        /** 费用币种。 */ String currency,
        /** 图片原生尺寸，视频配置返回空列表。 */ List<String> sizes
) {
    /** 视频报价由画幅及秒数另行计算。 */
    public ImageGenerationOption(GenerationProviderKey provider, Long configId, String name, String model,
            BigDecimal estimatedCost, String currency) {
        this(provider, configId, name, model, estimatedCost, currency, List.of());
    }
}
