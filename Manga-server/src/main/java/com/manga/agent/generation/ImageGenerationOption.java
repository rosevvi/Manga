package com.manga.agent.generation;

import java.util.List;
/** 生成入口需要的最小配置摘要，不返回 API Key 或代理密码。 */
public record ImageGenerationOption(
        /** 供应商路由。 */ GenerationProviderKey provider,
        /** 配置主键，Fake 为空。 */ Long configId,
        /** 配置展示名称。 */ String name,
        /** 允许模型。 */ String model,
        /** 图片原生尺寸，视频配置返回空列表。 */ List<String> sizes
) { }
