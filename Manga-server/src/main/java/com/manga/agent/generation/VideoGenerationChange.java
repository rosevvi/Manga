package com.manga.agent.generation;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

/** 冻结单镜头视频提案。费用、模型和路由均由已保存的用户配置解析。 */
public record VideoGenerationChange(
        /** 当前项目的目标镜头。 */ @NotNull @Positive Long shotId,
        /** 所选视频配置；为空则使用该用户的视频默认项。 */ @Positive Long configId,
        /** 视频画面与运动提示词。 */ @NotBlank @Size(max = 4000) String prompt,
        /** 输出秒数。 */ @Min(4) @Max(12) int seconds,
        /** 输出宽高比。 */ @NotBlank String aspectRatio,
        /** 服务端冻结的模型。 */ String model,
        /** 服务端预估总价。 */ BigDecimal estimatedCost,
        /** 报价币种。 */ String currency,
        /** 冻结的无密钥连接配置。 */ GenerationConfigSnapshot configSnapshot
) { }
