package com.manga.agent.generation;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
/** 平台中继协议只提示查询，不允许回调直接写入成功状态和图片 URL。 */
public record GenerationCallback(
        /** 中继事件稳定 ID，重发必须相同。 */ @NotBlank @Size(max = 128) String eventId,
        /** 可查询的供应商作业 ID。 */ @NotBlank @Size(max = 128) String providerJobId
) { }
