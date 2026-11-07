package com.manga.agent.generation;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
/** 图片提案 Schema v1；预计费用由服务端计算，用户批准后才能创建付费提交意图。 */
public record ImageGenerationChange(
    /** 当前项目内的镜头。 */ @NotNull @Positive Long shotId,
    /** 供应商实现。 */ @NotNull GenerationProviderKey provider,
    /** 真实供应商使用的用户配置；Fake 可为空。 */ @Positive Long configId,
    /** 服务端允许的模型。 */ @NotBlank @Size(max=100) String model,
    /** 图片提示词。 */ @NotBlank @Size(max=4000) String prompt,
    /** 输出宽度像素。 */ @Min(512) @Max(2048) int width,
    /** 输出高度像素。 */ @Min(512) @Max(2048) int height,
    /** 运维配置的预计费用，不是供应商硬性扣费上限；忽略模型传入值。 */ BigDecimal estimatedCost,
    /** 计价币种，由服务端决定。 */ String currency,
    /** 服务端冻结的计费端点和代理路由；模型传入值不会被信任。 */ GenerationConfigSnapshot configSnapshot
) { }
