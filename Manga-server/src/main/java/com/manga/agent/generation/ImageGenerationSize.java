package com.manga.agent.generation;

import com.manga.common.exception.BusinessException;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/** 镜头图片可选像素尺寸。Agnes 尺寸对应其原生 1K 画幅。 */
enum ImageGenerationSize {
    SQUARE(1024, 1024, "1:1"),
    LANDSCAPE(1536, 1024, null),
    PORTRAIT(1024, 1536, null),
    AGNES_LANDSCAPE(1248, 832, "3:2"),
    AGNES_PORTRAIT(832, 1248, "2:3");

    private final int width;
    private final int height;
    private final String agnesRatio;

    ImageGenerationSize(int width, int height, String agnesRatio) {
        this.width = width;
        this.height = height;
        this.agnesRatio = agnesRatio;
    }

    /** 返回生成表单可选择的实际产物尺寸。 */
    static List<String> options(GenerationProviderKey provider) {
        return Arrays.stream(values()).filter(size -> size.supports(provider))
                .map(size -> size.width + "x" + size.height).toList();
    }

    /** 匹配指定供应商支持的像素尺寸。 */
    static ImageGenerationSize require(GenerationProviderKey provider, int width, int height) {
        return Arrays.stream(values()).filter(size -> size.width == width && size.height == height && size.supports(provider))
                .findFirst().orElseThrow(() -> new BusinessException(GenerationError.INVALID));
    }

    /** 返回 Agnes 档位请求所用画幅。 */
    String agnesRatio() { return agnesRatio; }

    private boolean supports(GenerationProviderKey provider) {
        return this == SQUARE || (provider == GenerationProviderKey.AGNES) == Objects.nonNull(agnesRatio);
    }
}
